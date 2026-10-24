package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Resolves a COSMO nickname and reads that wallet's Objekt transfers directly,
 * so a collection can be shown without waiting for the full-chain backfill.
 */
@Service
public class CosmoCollectionService {
    private static final String TRANSFER_TOPIC = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";
    private static final long START_BLOCK = 6_800_000L;
    private static final long CHUNK = 12_000_000L;
    private static final int PAGE_SIZE = 24;

    private final RestClient cosmo;
    private final RestClient rpc;
    private final ObjectMapper mapper;
    private final String contract;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    public CosmoCollectionService(
            ObjectMapper mapper,
            @Value("${abstract.indexer.rpc-url:https://api.mainnet.abs.xyz}") String rpcUrl,
            @Value("${abstract.indexer.contract:0x99Bb83AE9bb0C0A6be865CaCF67760947f91Cb70}") String contract
    ) {
        this.mapper = mapper;
        this.contract = contract;
        this.cosmo = RestClient.builder().baseUrl("https://api.cosmo.fans").build();
        this.rpc = RestClient.builder().baseUrl(rpcUrl).build();
    }

    public Map<String, Object> search(String rawNickname, int page) {
        String nickname = rawNickname == null ? "" : rawNickname.trim();
        if (nickname.length() < 2 || nickname.length() > 40 || nickname.contains("/") || nickname.contains(" ")) {
            throw new IllegalArgumentException("닉네임은 2자 이상으로 입력해 주세요.");
        }
        Cached found = cache.compute(nickname.toLowerCase(), (key, current) -> {
            if (current != null && current.expiresAt > Instant.now().getEpochSecond()) return current;
            return load(nickname);
        });
        if (found.address.isBlank()) {
            throw new IllegalArgumentException("'" + nickname + "' 닉네임의 COSMO 계정을 찾지 못했습니다.");
        }
        int size = PAGE_SIZE;
        int from = Math.max(0, page) * size;
        List<Long> slice = from >= found.tokenIds.size() ? List.of() : found.tokenIds.subList(from, Math.min(found.tokenIds.size(), from + size));
        List<Map<String, Object>> cards = cards(slice);
        int totalPages = Math.max(1, (int) Math.ceil(found.tokenIds.size() / (double) size));
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("nickname", found.nickname);
        view.put("address", found.address);
        view.put("addressShort", shorten(found.address));
        view.put("profileImageUrl", GravityService.safeImage(found.image));
        view.put("hasProfileImage", !GravityService.safeImage(found.image).isBlank());
        view.put("ownedCount", found.tokenIds.size());
        view.put("cards", cards);
        view.put("hasCards", !cards.isEmpty());
        view.put("pageDisplay", Math.max(0, page) + 1);
        view.put("totalPages", totalPages);
        view.put("hasPrev", page > 0);
        view.put("hasNext", page + 1 < totalPages);
        view.put("prevPage", Math.max(0, page - 1));
        view.put("nextPage", page + 1);
        return view;
    }

    private Cached load(String nickname) {
        JsonNode profile = cosmo.get()
                .uri("/bff/v3/users/by-nickname/" + UriUtils.encodePath(nickname, StandardCharsets.UTF_8))
                .retrieve().body(JsonNode.class);
        if (profile == null || profile.path("address").asText("").isBlank()) {
            return new Cached(nickname, "", "", List.of(), Instant.now().getEpochSecond() + 60);
        }
        String address = profile.path("address").asText().toLowerCase();
        long head = hexLong(rpcCall("eth_blockNumber", mapper.createArrayNode()).asText());
        List<AbstractTransferLog> logs = new ArrayList<>();
        List<Callable<List<AbstractTransferLog>>> tasks = new ArrayList<>();
        for (long from = START_BLOCK; from <= head; from += CHUNK) {
            long to = Math.min(from + CHUNK - 1, head);
            long start = from;
            tasks.add(() -> fetch(start, to, address, true));
            tasks.add(() -> fetch(start, to, address, false));
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, Math.max(1, tasks.size())));
        try {
            for (Future<List<AbstractTransferLog>> future : pool.invokeAll(tasks)) {
                logs.addAll(future.get());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("이 지갑의 Abstract 전송을 읽지 못했습니다. 잠시 후 다시 검색해 주세요.");
        } finally {
            pool.shutdown();
        }
        return new Cached(profile.path("nickname").asText(nickname), address,
                profile.path("profileImageUrl").asText(""),
                currentlyHeld(logs, address), Instant.now().getEpochSecond() + 600);
    }

    private List<AbstractTransferLog> fetch(long from, long to, String address, boolean incoming) {
        ArrayNode topics = mapper.createArrayNode().add(TRANSFER_TOPIC);
        String topic = "0x" + "0".repeat(24) + address.substring(2);
        if (incoming) topics.addNull().add(topic);
        else topics.add(topic).addNull();
        ObjectNode filter = mapper.createObjectNode();
        filter.put("address", contract);
        filter.set("topics", topics);
        filter.put("fromBlock", "0x" + Long.toHexString(from));
        filter.put("toBlock", "0x" + Long.toHexString(to));
        JsonNode result = rpcCall("eth_getLogs", mapper.createArrayNode().add(filter));
        return AbstractTransferLog.parse(result);
    }

    static List<Long> currentlyHeld(List<AbstractTransferLog> logs, String address) {
        String owner = address.toLowerCase();
        List<AbstractTransferLog> ordered = new ArrayList<>(logs);
        ordered.sort(Comparator.comparingLong(AbstractTransferLog::blockNumber).thenComparingInt(AbstractTransferLog::logIndex));
        Map<Long, Held> owned = new LinkedHashMap<>();
        for (AbstractTransferLog log : ordered) {
            if (owner.equals(log.toAddress())) {
                owned.put(log.tokenId(), new Held(log.tokenId(), log.blockNumber(), log.logIndex()));
            } else if (owner.equals(log.fromAddress())) {
                owned.remove(log.tokenId());
            }
        }
        List<Held> held = new ArrayList<>(owned.values());
        held.sort(Comparator.comparingLong(Held::blockNumber).thenComparingInt(Held::logIndex).reversed());
        return held.stream().map(Held::tokenId).toList();
    }

    private List<Map<String, Object>> cards(List<Long> tokenIds) {
        if (tokenIds.isEmpty()) return List.of();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, tokenIds.size()));
        try {
            List<Future<Map<String, Object>>> futures = new ArrayList<>();
            for (Long tokenId : tokenIds) futures.add(pool.submit(() -> card(tokenId)));
            List<Map<String, Object>> cards = new ArrayList<>();
            for (Future<Map<String, Object>> future : futures) cards.add(future.get());
            return cards;
        } catch (Exception ex) {
            throw new IllegalStateException("오브젝트 정보를 불러오지 못했습니다.");
        } finally {
            pool.shutdown();
        }
    }

    private Map<String, Object> card(long tokenId) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("tokenId", String.valueOf(tokenId));
        card.put("collectionId", String.valueOf(tokenId));
        card.put("member", "");
        card.put("season", "");
        card.put("objektClass", "");
        card.put("thumbnailImage", "");
        card.put("hasImage", false);
        try {
            JsonNode meta = cosmo.get().uri("/bff/v3/objekts/nft-metadata/" + tokenId)
                    .retrieve().body(JsonNode.class);
            if (meta != null && meta.isObject()) {
                String image = GravityService.safeImage(meta.path("image").asText(""));
                card.put("collectionId", meta.path("name").asText(String.valueOf(tokenId)));
                card.put("member", trait(meta, "Member"));
                card.put("season", trait(meta, "Season"));
                card.put("objektClass", trait(meta, "Class"));
                card.put("thumbnailImage", image);
                card.put("hasImage", !image.isBlank());
            }
        } catch (RuntimeException ignored) {
            // The card still shows the token id when metadata is briefly unavailable.
        }
        return card;
    }

    private JsonNode rpcCall(String method, JsonNode params) {
        ObjectNode body = mapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", 1);
        body.put("method", method);
        body.set("params", params);
        JsonNode response = rpc.post().contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        if (response == null || response.has("error")) {
            throw new IllegalStateException(method + " failed");
        }
        return response.path("result");
    }

    private static String trait(JsonNode meta, String type) {
        for (JsonNode attribute : meta.path("attributes")) {
            if (type.equalsIgnoreCase(attribute.path("trait_type").asText())) return attribute.path("value").asText("");
        }
        return "";
    }

    private static long hexLong(String hex) {
        String value = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
        return Long.parseUnsignedLong(value, 16);
    }

    private static String shorten(String address) {
        if (address.length() < 12) return address;
        return address.substring(0, 6) + "…" + address.substring(address.length() - 4);
    }

    private record Held(long tokenId, long blockNumber, int logIndex) {}

    private record Cached(String nickname, String address, String image, List<Long> tokenIds, long expiresAt) {}
}
