package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.JsonNode;
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

/** Public collection index supplies serial -> token mapping; Abstract verifies current ownership. */
@Service
public class ObjektMarketDataService {
    private final RestClient index;
    private final RestClient cosmo;
    private final RestClient rpc;
    private final String contract;
    private volatile Map<String, Long> mintCounts = Map.of();

    public ObjektMarketDataService(
            @Value("${objekt.api.base-url:https://objekt.top/api/v1}") String indexUrl,
            @Value("${cosmo.api.base-url:https://api.cosmo.fans}") String cosmoUrl,
            @Value("${abstract.indexer.rpc-url:https://api.mainnet.abs.xyz}") String rpcUrl,
            @Value("${abstract.indexer.contract:0x99Bb83AE9bb0C0A6be865CaCF67760947f91Cb70}") String contract) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(6));
        this.index = RestClient.builder().baseUrl(indexUrl).requestFactory(factory).build();
        this.cosmo = RestClient.builder().baseUrl(cosmoUrl).requestFactory(factory).build();
        this.rpc = RestClient.builder().baseUrl(rpcUrl).requestFactory(factory).build();
        this.contract = contract;
    }

    // One bulk request, never one request per card or an on-demand whole-chain scan.
    @Scheduled(fixedDelayString = "${objekt.mints.refresh-ms:300000}",
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
                next.put(slug, row.path("count").asLong());
            }
            mintCounts = Map.copyOf(next);
        } catch (RuntimeException ignored) {
            // Preserve the last complete snapshot; unknown values must not become zero.
        }
    }

    public List<Map<String, Object>> withMintCounts(List<Map<String, Object>> items) {
        Map<String, Long> snapshot = mintCounts;
        return items.stream().map(item -> {
            Map<String, Object> copy = new LinkedHashMap<>(item);
            Long count = snapshot.get(String.valueOf(item.get("slug")));
            if (count != null) {
                copy.put("mintCount", count);
                copy.put("mintCountKnown", true);
            }
            return copy;
        }).toList();
    }

    public Map<String, Object> owner(Map<String, Object> collection, long serial) {
        if (serial < 1) throw new IllegalArgumentException("시리얼은 1 이상의 정수로 입력해 주세요.");
        String slug = String.valueOf(collection.get("slug"));
        JsonNode indexed;
        try {
            indexed = index.get().uri("/collections/{slug}/serials/{serial}/transfers", slug, serial)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) return Map.of("status", "not_found");
            throw e;
        }
        if (indexed == null) throw new IllegalStateException("시리얼 응답이 없습니다.");
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
        if (!result.matches("0x0{24}[0-9a-fA-F]{40}"))
            throw new IllegalStateException("Abstract 소유자 검증에 실패했습니다.");
        String address = "0x" + result.substring(26).toLowerCase(java.util.Locale.ROOT);
        if (address.equals("0x" + "0".repeat(40))) return Map.of("status", "burned");

        String nickname = "";
        // A historical recipient's nickname must never be attributed to a different current owner.
        for (JsonNode transfer : indexed.path("transfers")) {
            if (address.equalsIgnoreCase(transfer.path("to").asText()) && !transfer.path("nickname").isNull()) {
                nickname = transfer.path("nickname").asText("");
                break;
            }
        }
        return Map.of("status", "found", "serial", serial, "tokenId", token, "address", address,
                "nickname", nickname, "verifiedAt", Instant.now().toString(),
                "explorerUrl", "https://abscan.org/token/" + contract + "?a=" + token);
    }
}
