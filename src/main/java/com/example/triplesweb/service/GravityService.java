package com.example.triplesweb.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class GravityService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() {};
    private static final long LIST_TTL_MS = 60_000L;
    private static final long DETAIL_TTL_MS = 30_000L;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String cosmoApiBaseUrl;
    private final CosmoTokenProvider tokens;
    private final ConcurrentHashMap<String, CachedList> listCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CachedDetail> detailCache = new ConcurrentHashMap<>();
    private final ExecutorService refreshExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "gravity-cache-refresh");
        t.setDaemon(true);
        return t;
    });

    @org.springframework.beans.factory.annotation.Autowired
    public GravityService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${gravity.cosmo-api-base-url:https://api.cosmo.fans}") String cosmoApiBaseUrl,
            CosmoTokenProvider tokens
    ) {
        this(restClientBuilder.clone().requestFactory(CosmoTokenProvider.requestFactory()).build(),
                objectMapper, cosmoApiBaseUrl, tokens);
    }

    GravityService(RestClient client, ObjectMapper objectMapper, String cosmoApiBaseUrl, CosmoTokenProvider tokens) {
        this.restClient = client;
        this.objectMapper = objectMapper;
        this.cosmoApiBaseUrl = trimTrailingSlash(cosmoApiBaseUrl);
        this.tokens = tokens;
    }

    public boolean hasCosmoToken() {
        return tokens.isConfigured();
    }

    /**
     * Loads Gravity metadata from COSMO. Requires a valid, authorized access token.
     * The API response has upcoming, ongoing and past arrays.
     * Fresh for {@link #LIST_TTL_MS}; after expiry returns stale while refreshing in background.
     */
    public List<Map<String, Object>> fetchGravityList(String artistId) {
        requireCosmoToken();
        String key = artistId == null ? "" : artistId;
        long now = System.currentTimeMillis();
        CachedList cached = listCache.get(key);
        if (cached != null) {
            if (cached.expiresAt > now) {
                return copyList(cached.value);
            }
            // stale-while-revalidate
            if (cached.refreshing.compareAndSet(false, true)) {
                CompletableFuture.runAsync(() -> {
                    try {
                        listCache.put(key, new CachedList(loadGravityList(key), System.currentTimeMillis() + LIST_TTL_MS));
                    } catch (RuntimeException ignored) {
                        // keep stale
                    } finally {
                        CachedList current = listCache.get(key);
                        if (current != null) current.refreshing.set(false);
                    }
                }, refreshExecutor);
            }
            return copyList(cached.value);
        }
        CachedList fresh = new CachedList(loadGravityList(key), now + LIST_TTL_MS);
        listCache.put(key, fresh);
        return copyList(fresh.value);
    }

    private List<Map<String, Object>> loadGravityList(String artistId) {
        String encodedArtist = UriUtils.encodeQueryParam(artistId, StandardCharsets.UTF_8);
        JsonNode root = cosmoGet("/bff/v3/gravities?artistId=" + encodedArtist);
        if (!root.path("ongoing").isArray() || !root.path("upcoming").isArray() || !root.path("past").isArray()) {
            throw new IllegalStateException("COSMO 그래비티 목록 응답 형식이 변경되었습니다.");
        }
        List<Map<String, Object>> result = new ArrayList<>();

        appendGravityGroup(result, root.path("ongoing"), "진행 중", artistId);
        appendGravityGroup(result, root.path("upcoming"), "예정", artistId);
        appendGravityGroup(result, root.path("past"), "종료", artistId);

        return List.copyOf(result);
    }

    private void appendGravityGroup(
            List<Map<String, Object>> destination,
            JsonNode group,
            String statusLabel,
            String artistId
    ) {
        if (!group.isArray()) {
            return;
        }

        for (JsonNode gravityNode : group) {
            Map<String, Object> gravity = objectMapper.convertValue(gravityNode, MAP_TYPE);
            gravity.put("statusLabel", statusLabel);
            gravity.put("bannerImageUrl", safeImage(gravityNode.path("bannerImageUrl").asText("")));
            gravity.put("detailUrl", "/cosmo/gravity/" + gravityNode.path("id").asLong());
            gravity.put("artistId", artistId);
            JsonNode resultNode = gravityNode.path("result");
            boolean hasComo = resultNode.path("totalComoUsed").isNumber();
            gravity.put("hasComo", hasComo);
            gravity.put("comoUsed", hasComo ? resultNode.path("totalComoUsed").asLong() : 0L);


            List<Map<String, Object>> polls = new ArrayList<>();
            JsonNode pollNodes = gravityNode.path("polls");
            if (pollNodes.isArray()) {
                for (JsonNode pollNode : pollNodes) {
                    Map<String, Object> poll = objectMapper.convertValue(pollNode, MAP_TYPE);
                    poll.put("artistId", artistId);
                    poll.put("gravityTitle", gravityNode.path("title").asText("Gravity"));
                    poll.put("gravityId", gravityNode.path("id").asLong());
                    polls.add(poll);
                }
            }
            gravity.put("polls", polls);
            destination.add(gravity);
        }
    }

    /**
     * Candidate metadata comes from COSMO. The supplied ID is the COSMO poll ID,
     * not the parent Gravity ID.
     */
    public JsonNode fetchPollDetails(long pollId) {
        requireCosmoToken();
        if (pollId <= 0) {
            throw new IllegalArgumentException("pollId must be positive");
        }

        JsonNode response = cosmoGet("/bff/v3/polls/" + pollId);
        JsonNode details = response.path("pollDetail");
        if (!details.isObject()) throw new IllegalStateException("COSMO 투표 응답 형식이 변경되었습니다.");
        return details;
    }

    public Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return objectMapper.convertValue(node, MAP_TYPE);
    }

    public JsonNode fetchGravityDetails(long gravityId) {
        if (gravityId <= 0) throw new IllegalArgumentException("Invalid gravity ID");
        long now = System.currentTimeMillis();
        CachedDetail cached = detailCache.get(gravityId);
        if (cached != null) {
            if (cached.expiresAt > now) {
                return cached.value;
            }
            if (cached.refreshing.compareAndSet(false, true)) {
                CompletableFuture.runAsync(() -> {
                    try {
                        detailCache.put(gravityId, new CachedDetail(loadGravityDetails(gravityId),
                                System.currentTimeMillis() + DETAIL_TTL_MS));
                    } catch (RuntimeException ignored) {
                        // keep stale
                    } finally {
                        CachedDetail current = detailCache.get(gravityId);
                        if (current != null) current.refreshing.set(false);
                    }
                }, refreshExecutor);
            }
            return cached.value;
        }
        JsonNode fresh = loadGravityDetails(gravityId);
        detailCache.put(gravityId, new CachedDetail(fresh, now + DETAIL_TTL_MS));
        return fresh;
    }

    private JsonNode loadGravityDetails(long gravityId) {
        JsonNode response = cosmoGet("/bff/v3/gravities/" + gravityId);
        JsonNode gravity = response.path("gravity");
        if (!gravity.isObject()) throw new IllegalStateException("COSMO 그래비티 응답을 확인할 수 없습니다.");
        return gravity;
    }

    private JsonNode cosmoGet(String path) {
        String token = tokens.accessToken();
        try {
            return getWithToken(path, token);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 401) throw e;
            return getWithToken(path, tokens.afterUnauthorized(token));
        }
    }

    private JsonNode getWithToken(String path, String token) {
        JsonNode response = restClient.get().uri(cosmoApiBaseUrl + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.USER_AGENT, "triplesweb/1.0")
                .exchange((request, reply) -> {
                    if (!reply.getStatusCode().is2xxSuccessful()) {
                        throw new RestClientResponseException("COSMO API request failed",
                                reply.getStatusCode().value(), "", null, new byte[0], StandardCharsets.UTF_8);
                    }
                    String body = new String(reply.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    return tokens.decodeResponse(body, "1".equals(reply.getHeaders().getFirst("x-cosmo-encrypted")));
                });
        if (response == null || response.has("error")) {
            throw new IllegalStateException("COSMO API에서 올바른 응답을 받지 못했습니다.");
        }
        return response;
    }

    public JsonNode getAuthorized(String path) {
        requireCosmoToken();
        return cosmoGet(path);
    }

    public java.util.Optional<JsonNode> tryGet(String path) {
        if (!hasCosmoToken()) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(cosmoGet(path));
        } catch (RuntimeException ex) {
            return java.util.Optional.empty();
        }
    }

    private void requireCosmoToken() {
        if (!hasCosmoToken()) throw new IllegalStateException("COSMO 인증이 아직 연결되지 않았습니다.");
    }

    public static String safeImage(String url) {
        return url.startsWith("https://") ? url : "";
    }

    private static List<Map<String, Object>> copyList(List<Map<String, Object>> source) {
        List<Map<String, Object>> copy = new ArrayList<>(source.size());
        for (Map<String, Object> item : source) {
            copy.add(new LinkedHashMap<>(item));
        }
        return copy;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("/+$", "");
    }

    public static String describeApiError(Exception exception) {
        if (exception instanceof RestClientResponseException responseException) {
            return "외부 API 응답 오류 (HTTP " + responseException.getStatusCode().value() + "). " +
                    "접근 권한, ID 또는 API 응답 형식을 확인해 주세요.";
        }
        if (exception instanceof IllegalStateException) {
            return exception.getMessage();
        }
        if (exception instanceof IllegalArgumentException) {
            return "올바른 양의 숫자 투표 ID를 입력해 주세요.";
        }
        return "외부 API에 연결하지 못했습니다. 네트워크 또는 운영 서버의 API 접근 정책을 확인해 주세요.";
    }

    private static final class CachedList {
        final List<Map<String, Object>> value;
        final long expiresAt;
        final AtomicBoolean refreshing = new AtomicBoolean(false);

        CachedList(List<Map<String, Object>> value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }

    private static final class CachedDetail {
        final JsonNode value;
        final long expiresAt;
        final AtomicBoolean refreshing = new AtomicBoolean(false);

        CachedDetail(JsonNode value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }
}
