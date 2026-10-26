package com.example.triplesweb.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Internal account data; never returned directly by a public controller. */
@Service
public class CosmoObjektSnapshotStore {
    public record Card(String tokenId, String collectionId, Long serial, String acquiredAt,
                       String status, String collectionJson) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjektIngestStore ingest;
    private final boolean confirmSerials;

    public CosmoObjektSnapshotStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this(jdbc, manager, null, false);
    }

    @Autowired
    public CosmoObjektSnapshotStore(JdbcTemplate jdbc, PlatformTransactionManager manager,
                                    ObjektIngestStore ingest, @Value("${objekt.ingest.enabled:true}") boolean confirmSerials) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        this.ingest = ingest;
        this.confirmSerials = confirmSerials;
    }

    public int save(String scope, int collections, List<Card> cards) {
        return Objects.requireNonNull(transaction.execute(tx -> {
            Map<String, Card> existing = new HashMap<>();
            jdbc.query("SELECT * FROM cosmo_owned_objekt WHERE scope_key = ?", (rs, n) -> new Card(
                    rs.getString("token_id"), rs.getString("collection_id"), rs.getObject("serial_number", Long.class),
                    rs.getString("acquired_at"), rs.getString("inventory_status"), rs.getString("collection_json")), scope)
                    .forEach(card -> existing.put(card.tokenId(), card));
            int changed = 0;
            for (Card card : cards) {
                Card previous = existing.remove(card.tokenId());
                if (card.equals(previous)) continue;
                if (previous == null) jdbc.update("INSERT INTO cosmo_owned_objekt "
                                + "(collection_id, serial_number, acquired_at, inventory_status, collection_json, scope_key, token_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        card.collectionId(), card.serial(), card.acquiredAt(), card.status(), card.collectionJson(), scope, card.tokenId());
                else jdbc.update("UPDATE cosmo_owned_objekt SET collection_id=?, serial_number=?, acquired_at=?, inventory_status=?, collection_json=? WHERE scope_key=? AND token_id=?",
                        card.collectionId(), card.serial(), card.acquiredAt(), card.status(), card.collectionJson(), scope, card.tokenId());
                changed++;
            }
            for (String removed : existing.keySet()) {
                jdbc.update("DELETE FROM cosmo_owned_objekt WHERE scope_key=? AND token_id=?", scope, removed);
                changed++;
            }
            int updated = jdbc.update("UPDATE cosmo_objekt_sync_state SET collection_count=?, objekt_count=?, synced_at=? WHERE scope_key=?",
                    collections, cards.size(), System.currentTimeMillis(), scope);
            if (updated == 0) jdbc.update("INSERT INTO cosmo_objekt_sync_state (scope_key, artist, collection_count, objekt_count, synced_at) VALUES (?, ?, ?, ?, ?)",
                    scope, "tripleS", collections, cards.size(), System.currentTimeMillis());
            if (confirmSerials) ingest.confirm(cards);
            return changed;
        }));
    }
}
