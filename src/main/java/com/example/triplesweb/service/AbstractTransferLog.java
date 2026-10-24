package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** One ERC-721 Transfer log from the COSMO Objekt contract. */
record AbstractTransferLog(
        long blockNumber,
        int logIndex,
        String txHash,
        long tokenId,
        String fromAddress,
        String toAddress,
        Long blockTimestamp
) {
    static List<AbstractTransferLog> parse(JsonNode logs) {
        List<AbstractTransferLog> rows = new ArrayList<>();
        if (logs == null || !logs.isArray()) return rows;
        for (JsonNode log : logs) {
            if (log.path("removed").asBoolean(false)) continue;
            JsonNode topics = log.path("topics");
            if (!topics.isArray() || topics.size() < 4) continue;
            try {
                rows.add(new AbstractTransferLog(
                        hexLong(log.path("blockNumber").asText()),
                        (int) hexLong(log.path("logIndex").asText()),
                        log.path("transactionHash").asText("").toLowerCase(),
                        hexLong(topics.get(3).asText()),
                        address(topics.get(1).asText()),
                        address(topics.get(2).asText()),
                        log.hasNonNull("blockTimestamp") ? hexLong(log.path("blockTimestamp").asText()) : null
                ));
            } catch (RuntimeException ignored) {
                // A non-standard log should not stop the rest of the chunk.
            }
        }
        rows.sort((a, b) -> {
            int block = Long.compare(a.blockNumber, b.blockNumber);
            return block != 0 ? block : Integer.compare(a.logIndex, b.logIndex);
        });
        return rows;
    }

    private static long hexLong(String hex) {
        String value = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
        if (value.isBlank()) return 0L;
        return Long.parseUnsignedLong(value, 16);
    }

    private static String address(String topic) {
        String hex = topic.startsWith("0x") || topic.startsWith("0X") ? topic.substring(2) : topic;
        if (hex.length() < 40) hex = "0".repeat(40 - hex.length()) + hex;
        return "0x" + hex.substring(hex.length() - 40).toLowerCase();
    }
}
