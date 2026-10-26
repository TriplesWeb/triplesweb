package com.example.triplesweb.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;

/** A page and its cursor are committed together. Transfers are idempotent across overlapping scans. */
@Service
public class ObjektIngestStore {
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
        jdbc.update("INSERT IGNORE INTO objekt_ingest_cursor (lane, watermark_ms) VALUES (?, 0)", lane);
        return jdbc.queryForObject("SELECT * FROM objekt_ingest_cursor WHERE lane=?", (rs, n) -> new State(
                lane, rs.getLong("watermark_ms"), rs.getObject("cycle_head_ms", Long.class),
                rs.getObject("lower_bound_ms", Long.class),
                rs.getString("cursor_time") == null ? null : new ObjektActivityClient.Cursor(rs.getString("cursor_time"), rs.getString("cursor_id")),
                rs.getLong("version"), rs.getBoolean("complete"), rs.getObject("last_success_ms", Long.class)), lane);
    }

    /** Returns false when another worker won the same lane while this one fetched a page. */
    public boolean commit(State expected, ObjektActivityClient.Page page, long now, long cycleHead,
                          long lowerBound, boolean done) {
        return Objects.requireNonNull(transaction.execute(tx -> {
            Long version = jdbc.queryForObject("SELECT version FROM objekt_ingest_cursor WHERE lane=? FOR UPDATE",
                    Long.class, expected.lane());
            if (version == null || version != expected.version()) return false;
            for (ObjektActivityClient.Event event : page.events()) {
                jdbc.update("INSERT IGNORE INTO objekt_observed_transfer (source_id, token_id, from_address, to_address, tx_hash, transferred_at_ms, observed_at_ms) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        event.id(), event.tokenId(), event.from(), event.to(), event.hash(), event.time(), now);
                jdbc.update("INSERT IGNORE INTO objekt_observed_token (token_id) VALUES (?)", event.tokenId());
                // Older backfill rows can fill gaps but cannot replace a more recent indexed owner.
                jdbc.update("UPDATE objekt_observed_token SET collection_slug=COALESCE(collection_slug, ?), "
                                + "collection_id=COALESCE(collection_id, ?), artist=COALESCE(artist, ?), "
                                + "reported_serial=CASE WHEN ? IS NOT NULL AND (serial_observed_at_ms IS NULL OR serial_observed_at_ms <= ?) THEN ? ELSE reported_serial END, "
                                + "serial_observed_at_ms=CASE WHEN ? IS NOT NULL AND (serial_observed_at_ms IS NULL OR serial_observed_at_ms <= ?) THEN ? ELSE serial_observed_at_ms END WHERE token_id=?",
                        event.slug(), event.collection(), event.artist(), event.serial(), now, event.serial(),
                        event.serial(), now, now, event.tokenId());
                jdbc.update("UPDATE objekt_observed_token SET indexed_owner=?, last_transfer_ms=?, last_transfer_id=? "
                                + "WHERE token_id=? AND (last_transfer_ms IS NULL OR last_transfer_ms < ? OR (last_transfer_ms = ? AND last_transfer_id < ?))",
                        event.to(), event.time(), event.id(), event.tokenId(), event.time(), event.time(), event.id());
            }
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
