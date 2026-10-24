package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.sql.Types;
import java.util.List;

/**
 * Follows every Objekt Transfer on Abstract and stores it in MariaDB.
 * Off by default: the table grows faster than the shared database can absorb,
 * and nickname search already reads one wallet directly.
 */
@Service
public class AbstractObjektIndexer {
    private static final Logger log = LoggerFactory.getLogger(AbstractObjektIndexer.class);
    private static final String TRANSFER_TOPIC = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    private final boolean enabled;
    private final String contract;
    private final long startBlock;
    private final int chunkSize;
    private final int chunksPerTick;
    private final RestClient rpc;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;

    public AbstractObjektIndexer(
            @Value("${abstract.indexer.enabled:false}") boolean enabled,
            @Value("${abstract.indexer.rpc-url:https://api.mainnet.abs.xyz}") String rpcUrl,
            @Value("${abstract.indexer.contract:0x99Bb83AE9bb0C0A6be865CaCF67760947f91Cb70}") String contract,
            @Value("${abstract.indexer.start-block:6800000}") long startBlock,
            @Value("${abstract.indexer.chunk-size:800}") int chunkSize,
            @Value("${abstract.indexer.chunks-per-tick:6}") int chunksPerTick,
            ObjectMapper mapper,
            JdbcTemplate jdbc
    ) {
        this.enabled = enabled;
        this.contract = contract;
        this.startBlock = startBlock;
        this.chunkSize = Math.max(50, chunkSize);
        this.chunksPerTick = Math.max(1, chunksPerTick);
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.rpc = RestClient.builder().baseUrl(rpcUrl).build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logStart() {
        if (!enabled) {
            log.info("Abstract Objekt indexer disabled");
            return;
        }
        log.info("Abstract Objekt indexer will collect transfers from block {}", cursor());
    }

    @Scheduled(fixedDelayString = "${abstract.indexer.delay-ms:2000}", initialDelayString = "${abstract.indexer.initial-delay-ms:15000}")
    public void collect() {
        if (!enabled) return;
        try {
            long head = hexLong(call("eth_blockNumber", mapper.createArrayNode()).asText());
            long next = cursor();
            for (int i = 0; i < chunksPerTick && next <= head; i++) {
                long end = Math.min(next + chunkSize - 1, head);
                List<AbstractTransferLog> logs = AbstractTransferLog.parse(logs(next, end));
                store(logs);
                jdbc.update("UPDATE abstract_index_state SET next_block = ? WHERE id = 1", end + 1);
                log.info("Abstract Objekt transfers {}-{} stored {} logs; next {}", next, end, logs.size(), end + 1);
                next = end + 1;
            }
        } catch (RuntimeException ex) {
            log.warn("Abstract Objekt indexer tick failed: {}", ex.toString());
        }
    }

    private JsonNode logs(long from, long to) {
        ObjectNode filter = mapper.createObjectNode();
        filter.put("address", contract);
        filter.putArray("topics").add(TRANSFER_TOPIC);
        filter.put("fromBlock", "0x" + Long.toHexString(from));
        filter.put("toBlock", "0x" + Long.toHexString(to));
        var params = mapper.createArrayNode().add(filter);
        return call("eth_getLogs", params);
    }

    private void store(List<AbstractTransferLog> logs) {
        if (logs.isEmpty()) return;
        jdbc.batchUpdate("""
                INSERT IGNORE INTO abstract_objekt_transfer
                    (block_number, log_index, tx_hash, token_id, from_address, to_address, block_timestamp)
                VALUES (?,?,?,?,?,?,?)
                """, logs, 400, (ps, row) -> {
            ps.setLong(1, row.blockNumber());
            ps.setInt(2, row.logIndex());
            ps.setString(3, row.txHash());
            ps.setLong(4, row.tokenId());
            ps.setString(5, row.fromAddress());
            ps.setString(6, row.toAddress());
            if (row.blockTimestamp() == null) ps.setNull(7, Types.BIGINT);
            else ps.setLong(7, row.blockTimestamp());
        });
        jdbc.batchUpdate("""
                INSERT INTO abstract_objekt_owner (token_id, owner_address, block_number, log_index, order_key)
                VALUES (?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    owner_address = IF(order_key <= VALUES(order_key), VALUES(owner_address), owner_address),
                    block_number = IF(order_key <= VALUES(order_key), VALUES(block_number), block_number),
                    log_index = IF(order_key <= VALUES(order_key), VALUES(log_index), log_index),
                    order_key = GREATEST(order_key, VALUES(order_key))
                """, logs, 400, (ps, row) -> {
            ps.setLong(1, row.tokenId());
            ps.setString(2, row.toAddress());
            ps.setLong(3, row.blockNumber());
            ps.setInt(4, row.logIndex());
            ps.setLong(5, row.blockNumber() * 1_000_000L + row.logIndex());
        });
    }

    private long cursor() {
        Long stored = jdbc.query("SELECT next_block FROM abstract_index_state WHERE id = 1",
                rs -> rs.next() ? rs.getLong(1) : null);
        if (stored == null || (stored == 7_200_000L && startBlock < stored)) {
            jdbc.update("""
                    INSERT INTO abstract_index_state (id, next_block) VALUES (1, ?)
                    ON DUPLICATE KEY UPDATE next_block = VALUES(next_block)
                    """, startBlock);
            return startBlock;
        }
        return Math.max(stored, startBlock);
    }

    private JsonNode call(String method, JsonNode params) {
        ObjectNode body = mapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", 1);
        body.put("method", method);
        body.set("params", params);
        JsonNode response = rpc.post().contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        if (response == null) throw new IllegalStateException(method + " returned an empty response");
        if (response.has("error")) throw new IllegalStateException(method + " " + response.path("error").path("message").asText("failed"));
        return response.path("result");
    }

    private static long hexLong(String hex) {
        String value = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
        return Long.parseUnsignedLong(value, 16);
    }
}
