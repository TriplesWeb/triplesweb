package com.example.triplesweb.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Types;
import java.util.List;
import java.util.Objects;

/** A page and its cursor are committed together. Transfers are idempotent across overlapping scans. */
@Service
public class ObjektIngestStore {
    private static final int BATCH_SIZE = 300;
    private static final String NEWER_TRANSFER = """
            last_transfer_ms IS NULL OR last_transfer_ms < VALUES(last_transfer_ms)
            OR (last_transfer_ms = VALUES(last_transfer_ms) AND last_transfer_id < VALUES(last_transfer_id))
            """;

    public record State(String lane, long watermark, Long cycleHead, Long lowerBound,
                        ObjektActivityClient.Cursor cursor, long version, boolean complete, Long lastSuccess) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public ObjektIngestStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
    }

    public State state(String lane) {
        if (!lane.equals("live") && !lane.equals("backfill")) throw new IllegalArgumentException("Unknown lane");
        State existing = readState(lane);
        if (existing != null) return existing;
        jdbc.update("INSERT IGNORE INTO objekt_ingest_cursor (lane, watermark_ms) VALUES (?, 0)", lane);
        return Objects.requireNonNull(readState(lane));
    }

    private State readState(String lane) {
        return jdbc.query("SELECT * FROM objekt_ingest_cursor WHERE lane=?", rs -> rs.next() ? new State(
                lane, rs.getLong("watermark_ms"), rs.getObject("cycle_head_ms", Long.class),
                rs.getObject("lower_bound_ms", Long.class),
                rs.getString("cursor_time") == null ? null : new ObjektActivityClient.Cursor(rs.getString("cursor_time"), rs.getString("cursor_id")),
                rs.getLong("version"), rs.getBoolean("complete"), rs.getObject("last_success_ms", Long.class)) : null, lane);
    }

    /** Returns false when another worker won the same lane while this one fetched a page. */
    public boolean commit(State expected, ObjektActivityClient.Page page, long now, long cycleHead,
                          long lowerBound, boolean done) {
        return Objects.requireNonNull(transaction.execute(tx -> {
            Long version = jdbc.queryForObject("SELECT version FROM objekt_ingest_cursor WHERE lane=? FOR UPDATE",
                    Long.class, expected.lane());
            if (version == null || version != expected.version()) return false;
            storeEvents(page.events(), now);
            ObjektActivityClient.Cursor next = done ? null : page.next();
            jdbc.update("UPDATE objekt_ingest_cursor SET watermark_ms=?, cycle_head_ms=?, lower_bound_ms=?, cursor_time=?, cursor_id=?, "
                            + "version=version+1, complete=?, last_success_ms=? WHERE lane=?",
                    expected.lane().equals("live") && done ? Math.max(expected.watermark(), cycleHead) : expected.watermark(),
                    done ? null : cycleHead, done ? null : lowerBound,
                    next == null ? null : next.timestamp(), next == null ? null : next.id(),
                    expected.lane().equals("backfill") && done, now, expected.lane());
            return true;
        }));
    }

    private void storeEvents(List<ObjektActivityClient.Event> events, long now) {
        if (events.isEmpty()) return;
        jdbc.batchUpdate("""
                INSERT IGNORE INTO objekt_observed_transfer
                    (source_id, token_id, from_address, to_address, tx_hash, transferred_at_ms, observed_at_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, events, BATCH_SIZE, (statement, event) -> {
            statement.setString(1, event.id());
            statement.setString(2, event.tokenId());
            statement.setString(3, event.from());
            statement.setString(4, event.to());
            statement.setString(5, event.hash());
            statement.setLong(6, event.time());
            statement.setLong(7, now);
        });
        // MariaDB applies assignments in order. Compare against the old cursor before changing it.
        jdbc.batchUpdate("""
                INSERT INTO objekt_observed_token
                    (token_id, collection_slug, collection_id, artist, reported_serial, serial_observed_at_ms,
                     indexed_owner, last_transfer_ms, last_transfer_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    collection_slug = COALESCE(collection_slug, VALUES(collection_slug)),
                    collection_id = COALESCE(collection_id, VALUES(collection_id)),
                    artist = COALESCE(artist, VALUES(artist)),
                    reported_serial = CASE WHEN COALESCE(serial_observed_at_ms, VALUES(serial_observed_at_ms))
                        <= VALUES(serial_observed_at_ms)
                        THEN VALUES(reported_serial) ELSE reported_serial END,
                    serial_observed_at_ms = CASE WHEN COALESCE(serial_observed_at_ms, VALUES(serial_observed_at_ms))
                        <= VALUES(serial_observed_at_ms)
                        THEN VALUES(serial_observed_at_ms) ELSE serial_observed_at_ms END,
                    indexed_owner = CASE WHEN %s THEN VALUES(indexed_owner) ELSE indexed_owner END,
                    last_transfer_id = CASE WHEN %s THEN VALUES(last_transfer_id) ELSE last_transfer_id END,
                    last_transfer_ms = GREATEST(COALESCE(last_transfer_ms, VALUES(last_transfer_ms)), VALUES(last_transfer_ms))
                """.formatted(NEWER_TRANSFER, NEWER_TRANSFER), events, BATCH_SIZE, (statement, event) -> {
            statement.setString(1, event.tokenId());
            statement.setString(2, event.slug());
            statement.setString(3, event.collection());
            statement.setString(4, event.artist());
            if (event.serial() == null) {
                statement.setNull(5, Types.BIGINT);
                statement.setNull(6, Types.BIGINT);
            } else {
                statement.setLong(5, event.serial());
                statement.setLong(6, now);
            }
            statement.setString(7, event.to());
            statement.setLong(8, event.time());
            statement.setString(9, event.id());
        });
    }

    /** Called inside the authenticated snapshot transaction; confirmed values never come from the public index. */
    public void confirm(List<CosmoObjektSnapshotStore.Card> cards) {
        long now = System.currentTimeMillis();
        for (CosmoObjektSnapshotStore.Card card : cards) {
            if (card.serial() == null) continue;
            jdbc.update("INSERT IGNORE INTO objekt_observed_token (token_id) VALUES (?)", card.tokenId());
            int updated = jdbc.update("UPDATE objekt_observed_token SET confirmed_serial=?, confirmed_collection_id=?, confirmed_at_ms=? "
                            + "WHERE token_id=? AND (confirmed_serial IS NULL OR (confirmed_serial=? AND confirmed_collection_id=?))",
                    card.serial(), card.collectionId(), now, card.tokenId(), card.serial(), card.collectionId());
            if (updated != 1) throw new IllegalStateException("확인된 시리얼이 기존 기록과 충돌합니다.");
        }
    }
}
