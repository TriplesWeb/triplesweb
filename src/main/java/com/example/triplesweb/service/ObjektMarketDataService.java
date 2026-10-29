package com.example.triplesweb.service;

import com.example.triplesweb.infra.GzipRestClients;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigInteger;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/** Public collection index supplies serial -> token mapping; Abstract verifies current ownership. */
@Service
public class ObjektMarketDataService {
    private final RestClient index;
    private final ObjektIndexClient historyIndex;
    private final RestClient cosmo;
    private final RestClient rpc;
    private final String contract;
    private volatile Map<String, Long> mintCounts = Map.of();
    private final ConcurrentHashMap<String, NicknameCache> nicknames = new ConcurrentHashMap<>();

    @Autowired
    public ObjektMarketDataService(
            @Value("${objekt.api.base-url:https://objekt.top/api/v1}") String indexUrl,
            @Value("${cosmo.api.base-url:https://api.cosmo.fans}") String cosmoUrl,
            @Value("${abstract.indexer.rpc-url:https://api.mainnet.abs.xyz}") String rpcUrl,
            @Value("${abstract.indexer.contract:0x99Bb83AE9bb0C0A6be865CaCF67760947f91Cb70}") String contract,
            ObjektIndexClient historyIndex) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(6));
        this.index = GzipRestClients.create(indexUrl, Duration.ofSeconds(3), Duration.ofSeconds(15));
        this.cosmo = RestClient.builder().baseUrl(cosmoUrl).requestFactory(factory).build();
        this.rpc = RestClient.builder().baseUrl(rpcUrl).requestFactory(factory).build();
        this.contract = contract;
        this.historyIndex = historyIndex;
    }

    ObjektMarketDataService(String indexUrl, String cosmoUrl, String rpcUrl, String contract) {
        this(indexUrl, cosmoUrl, rpcUrl, contract, new ObjektIndexClient(indexUrl));
    }

    // One bulk request, never one request per card or an on-demand whole-chain scan.
    @Scheduled(fixedDelayString = "${objekt.mints.refresh-ms:60000}",
            initialDelayString = "${objekt.mints.initial-delay-ms:1000}")
    public void refreshMintCounts() {
        try {
            JsonNode rows = index.get().uri("/collections/rarity").retrieve().body(JsonNode.class);
            if (rows == null || !rows.isArray() || rows.isEmpty()) return;
            Map<String, Long> next = new LinkedHashMap<>();
            for (JsonNode row : rows) {
                String slug = row.path("slug").asText();
                if (slug.isBlank() || !row.path("count").isIntegralNumber()
                        || !row.path("count").canConvertToLong() || row.path("count").asLong() < 0) return;
                if (next.putIfAbsent(slug, row.path("count").asLong()) != null) return;
            }
            mintCounts = Map.copyOf(next);
        } catch (RuntimeException ignored) {
            // Preserve the last complete snapshot; unknown values must not become zero.
        }
    }

    public List<Map<String, Object>> withMintCounts(List<Map<String, Object>> items) {
        Map<String, Long> snapshot = mintCounts;
        return items.stream().map(item -> applyMint(item, snapshot)).toList();
    }

    /** Mint lookup without copying the full catalog — used by sort comparators. */
    public Long mintCount(String slug) {
        if (slug == null || slug.isBlank()) return null;
        return mintCounts.get(slug);
    }

    private static Map<String, Object> applyMint(Map<String, Object> item, Map<String, Long> snapshot) {
        Map<String, Object> copy = new LinkedHashMap<>(item);
        Long count = snapshot.get(String.valueOf(item.get("slug")));
        if (count != null) {
            copy.put("mintCount", count);
            copy.put("mintCountKnown", true);
        }
        return copy;
    }

    public Map<String, Object> owner(Map<String, Object> collection, long serial) {
        if (serial < 1) throw new IllegalArgumentException("시리얼은 1 이상의 정수로 입력해 주세요.");
        String slug = String.valueOf(collection.get("slug"));
        JsonNode indexed = historyIndex.serialHistory(slug, serial);
        if (indexed == null) return Map.of("status", "not_found");
        if (indexed.path("hide").asBoolean()) return Map.of("status", "hidden");
        String token = indexed.path("tokenId").asText();
        if (token.isBlank()) return Map.of("status", "not_found");
        if (!token.matches("[0-9]{1,78}")) throw new IllegalStateException("잘못된 토큰 응답입니다.");

        // COSMO metadata names now contain token ID, not collection serial. Never infer one from the other.
        JsonNode metadata = cosmo.get().uri("/bff/v3/objekts/nft-metadata/{id}", token)
                .retrieve().body(JsonNode.class);
        if (metadata == null) throw new IllegalStateException("메타데이터가 없습니다.");
        Map<String, String> traits = new LinkedHashMap<>();
        for (JsonNode trait : metadata.path("attributes")) {
            traits.put(trait.path("trait_type").asText(), trait.path("value").asText());
        }
        for (var pair : Map.of("Member", "member", "Season", "season", "Collection", "collectionNo").entrySet()) {
            if (!String.valueOf(collection.get(pair.getValue())).equalsIgnoreCase(traits.getOrDefault(pair.getKey(), "")))
                throw new IllegalStateException("컬렉션과 토큰의 메타데이터가 일치하지 않습니다.");
        }
        String hex = new BigInteger(token).toString(16);
        if (hex.length() > 64) throw new IllegalStateException("잘못된 토큰 번호입니다.");
        JsonNode chain = rpc.post().body(Map.of("jsonrpc", "2.0", "id", 1, "method", "eth_call",
                "params", List.of(Map.of("to", contract, "data", "0x6352211e" + "0".repeat(64 - hex.length()) + hex), "latest")))
                .retrieve().body(JsonNode.class);
        String result = chain == null ? "" : chain.path("result").asText();
        if (chain.has("error") || !result.matches("0x0{24}[0-9a-fA-F]{40}"))
            throw new IllegalStateException("Abstract 소유자 검증에 실패했습니다.");
        String address = "0x" + result.substring(26).toLowerCase(java.util.Locale.ROOT);
        if (address.equals("0x" + "0".repeat(40))) return Map.of("status", "burned");

        List<String> candidates = new ArrayList<>();
        for (JsonNode transfer : indexed.path("transfers")) {
            if (address.equalsIgnoreCase(transfer.path("to").asText()) && !transfer.path("nickname").isNull()) {
                candidates.add(transfer.path("nickname").asText(""));
            }
        }
        String nickname = resolveNickname(address, candidates);
        return Map.of("status", "found", "serial", serial, "tokenId", token, "address", address,
                "nickname", nickname, "verifiedAt", Instant.now().toString(),
                "explorerUrl", "https://abscan.org/token/" + contract + "?a=" + token);
    }

    /** Indexed history is independent of live RPC health and is labelled as historical. */
    public Map<String, Object> transfers(Map<String, Object> collection, long serial, int page) {
        return serialLookup(collection, serial, page);
    }

    /** Latest recipient and history come from the same index snapshot, without depending on RPC health. */
    public Map<String, Object> serialLookup(Map<String, Object> collection, long serial, int page) {
        if (serial < 1 || page < 0) throw new IllegalArgumentException("시리얼과 페이지를 확인해 주세요.");
        JsonNode indexed = historyIndex.serialHistory(String.valueOf(collection.get("slug")), serial);
        if (indexed == null) return Map.of("status", "not_found");
        if (indexed.path("hide").asBoolean()) return Map.of("status", "hidden");
        JsonNode rows = indexed.path("transfers");
        if (rows.size() > 10000) throw new IllegalStateException("전송 내역이 너무 큽니다.");
        // Validate the entire upstream response before presenting any page.
        List<Map<String, Object>> history = new ArrayList<>();
        List<JsonNode> ordered = new ArrayList<>();
        for (JsonNode row : rows) {
            String timestamp = row.path("timestamp").asText("");
            try { timestamp = Instant.parse(timestamp).toString(); }
            catch (RuntimeException e) { throw new IllegalStateException("전송 일시를 확인하지 못했습니다."); }
            String nickname = row.path("nickname").asText("");
            if (nickname.length() > 100) throw new IllegalStateException("전송 계정 응답이 올바르지 않습니다.");
            history.add(Map.of("timestamp", timestamp, "nickname", nickname));
            ordered.add(row);
        }
        history.sort(java.util.Comparator.comparing(row -> Instant.parse((String) row.get("timestamp")),
                java.util.Comparator.reverseOrder()));
        ordered.sort(java.util.Comparator.comparing((JsonNode row) -> Instant.parse(row.path("timestamp").asText())).reversed());
        Map<String, Object> latestOwner = new LinkedHashMap<>();
        latestOwner.put("status", "unknown");
        latestOwner.put("basis", "latest_transfer");
        if (!ordered.isEmpty()) {
            JsonNode latest = ordered.get(0);
            String address = latest.path("to").asText("");
            boolean ambiguous = ordered.stream().skip(1).anyMatch(row ->
                    Instant.parse(row.path("timestamp").asText()).equals(Instant.parse(latest.path("timestamp").asText()))
                            && !address.equalsIgnoreCase(row.path("to").asText("")));
            if (!ambiguous && address.matches("0x[0-9a-fA-F]{40}")) {
                boolean burned = address.equalsIgnoreCase("0x" + "0".repeat(40));
                latestOwner.put("status", burned ? "burned" : "indexed");
                latestOwner.put("nickname", latest.path("nickname").asText(""));
                latestOwner.put("timestamp", Instant.parse(latest.path("timestamp").asText()).toString());
                String name = latest.path("nickname").asText("");
                if (!burned && !name.isBlank()) latestOwner.put("profileUrl", "/cosmo/profile/" + java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"));
            }
        }
        int pages = Math.max(1, (history.size() + 19) / 20);
        int safePage = Math.min(page, pages - 1), from = safePage * 20;
        return Map.of("status", "available", "serial", serial, "source", "objekt.top",
                "page", safePage, "total", history.size(), "hasNext", safePage + 1 < pages,
                "owner", latestOwner, "transfers", List.copyOf(history.subList(from, Math.min(from + 20, history.size()))));
    }

    private String resolveNickname(String address, List<String> candidates) {
        long now = Instant.now().getEpochSecond();
        nicknames.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        NicknameCache cached = nicknames.get(address);
        if (cached != null) return cached.nickname;
        try {
            JsonNode profile = index.get().uri("/profiles/{address}", address).retrieve().body(JsonNode.class);
            if (profile != null && address.equalsIgnoreCase(profile.path("address").asText()))
                candidates.add(0, profile.path("nickname").asText(""));
        } catch (RuntimeException ignored) {
            // A public history candidate may still resolve when the profile service is unavailable.
        }
        for (String candidate : candidates.stream().filter(s -> !s.isBlank()).distinct().limit(3).toList()) {
            try {
                JsonNode account = cosmo.get().uri("/bff/v3/users/by-nickname/{nickname}", candidate)
                        .retrieve().body(JsonNode.class);
                if (account != null && address.equalsIgnoreCase(account.path("address").asText())) {
                    String nickname = account.path("nickname").asText(candidate);
                    nicknames.put(address, new NicknameCache(nickname, now + 300));
                    return nickname;
                }
            } catch (RuntimeException ignored) {
                // A missing/renamed candidate must not hide a verified wallet or become a guessed username.
            }
        }
        return "";
    }

    private record NicknameCache(String nickname, long expiresAt) {}
}

