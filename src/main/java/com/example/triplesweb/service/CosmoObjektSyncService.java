package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Fetch every page before committing. No requests from public page rendering. */
@Service
public class CosmoObjektSyncService {
    public record Result(int collections, int objekts, int changed) {}
    private static final List<String> PUBLIC_FIELDS = List.of("collectionId", "artistName", "member", "season", "class",
            "collectionNo", "thumbnailImage", "frontImage", "backImage", "frontMedia", "hasVoice",
            "backgroundColor", "textColor", "createdAt", "updatedAt");
    private final RestClient client;
    private final CosmoTokenProvider tokens;
    private final CosmoObjektSnapshotStore store;
    private final ObjectMapper mapper;

    @Autowired
    public CosmoObjektSyncService(RestClient.Builder builder, CosmoTokenProvider tokens, CosmoObjektSnapshotStore store,
                                 ObjectMapper mapper, @Value("${gravity.cosmo-api-base-url:https://api.cosmo.fans}") String baseUrl) {
        this(builder.clone().baseUrl(baseUrl).requestFactory(CosmoTokenProvider.requestFactory()).build(), tokens, store, mapper);
    }
    CosmoObjektSyncService(RestClient client, CosmoTokenProvider tokens, CosmoObjektSnapshotStore store, ObjectMapper mapper) {
        this.client = client; this.tokens = tokens; this.store = store; this.mapper = mapper;
    }

    public synchronized Result sync() {
        // Local scope hashing is only isolation. COSMO must accept the token before any DB write.
        String token = tokens.accessToken();
        String scope = scope(token);
        Set<String> collectionIds = new HashSet<>(), tokenIds = new HashSet<>();
        List<CosmoObjektSnapshotStore.Card> cards = new ArrayList<>();
        int expected = -1;
        for (int page = 1; page <= 1000; page++) {
            JsonNode response;
            try { response = fetch(page, token); }
            catch (RestClientResponseException error) {
                if (error.getStatusCode().value() != 401) throw failure();
                token = tokens.afterUnauthorized(token);
                if (!scope.equals(scope(token))) throw failure();
                try { response = fetch(page, token); } catch (RuntimeException retryError) { throw failure(); }
            } catch (RuntimeException error) { throw failure(); }
            if (response == null || !response.path("collections").isArray() || !response.path("collectionCount").canConvertToInt()) throw failure();
            int count = response.path("collectionCount").asInt(-1);
            if (count < 0 || (expected >= 0 && count != expected)) throw failure();
            expected = count;
            for (JsonNode entry : response.path("collections")) {
                JsonNode collection = entry.path("collection"), objekts = entry.path("objekts");
                String id = collection.path("collectionId").asText("");
                if (id.isBlank() || id.length() > 120 || !"tripleS".equalsIgnoreCase(collection.path("artistName").asText())
                        || !collectionIds.add(id) || !objekts.isArray() || !entry.path("count").canConvertToInt()
                        || entry.path("count").asInt(-1) != objekts.size()) throw failure();
                var safeCollection = mapper.createObjectNode();
                PUBLIC_FIELDS.forEach(field -> { if (collection.has(field)) safeCollection.set(field, collection.get(field)); });
                for (JsonNode objekt : objekts) {
                    JsonNode metadata = objekt.path("metadata"), inventory = objekt.path("inventory");
                    String tokenId = metadata.path("tokenId").asText("");
                    if (!tokenId.matches("[0-9]{1,78}") || !tokenIds.add(tokenId)
                            || !id.equals(metadata.path("collectionId").asText()) || !inventory.isObject()) throw failure();
                    JsonNode serialValue = metadata.path("objektNo");
                    Long serial = serialValue.isMissingNode() || serialValue.isNull() ? null : serialValue.asLong(-1);
                    if (serial != null && serial <= 0) throw failure();
                    String acquired = inventory.path("acquiredAt").asText(""), status = inventory.path("status").asText("");
                    if (acquired.length() > 40 || status.length() > 40) throw failure();
                    cards.add(new CosmoObjektSnapshotStore.Card(tokenId, id, serial, acquired, status, safeCollection.toString()));
                }
            }
            if (collectionIds.size() == expected) return new Result(expected, cards.size(), store.save(scope, expected, cards));
            if (collectionIds.size() > expected || response.path("collections").isEmpty()) throw failure();
        }
        throw failure();
    }
    private JsonNode fetch(int page, String token) {
        return client.get().uri(builder -> builder.path("/bff/v3/objekt-summaries").queryParam("artistId", "tripleS")
                        .queryParam("order", "newest").queryParam("page", page).queryParam("size", 30).build())
                .header("Authorization", "Bearer " + token).header("User-Agent", "triplesweb/1.0")
                .exchange((request, reply) -> {
                    if (!reply.getStatusCode().is2xxSuccessful()) throw new RestClientResponseException("COSMO request failed",
                            reply.getStatusCode().value(), "", null, new byte[0], StandardCharsets.UTF_8);
                    String body = new String(reply.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    return tokens.decodeResponse(body, "1".equals(reply.getHeaders().getFirst("x-cosmo-encrypted")));
                });
    }
    private String scope(String token) {
        try {
            String subject = mapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1])).path("sub").asText("");
            if (subject.isBlank()) throw failure();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(("cosmo:tripleS:" + subject).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw failure(); }
    }
    private static IllegalStateException failure() {
        return new IllegalStateException("COSMO 보유 목록을 완전히 확인하지 못했습니다. 기존 DB를 유지합니다. 연결 상태를 확인한 뒤 다시 시도해 주세요.");
    }
}
