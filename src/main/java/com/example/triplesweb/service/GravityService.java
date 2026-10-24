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

@Service
public class GravityService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() {};

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String cosmoApiBaseUrl;
    private final CosmoTokenProvider tokens;

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
     */
    public List<Map<String, Object>> fetchGravityList(String artistId) {
        requireCosmoToken();

        String encodedArtist = UriUtils.encodeQueryParam(artistId, StandardCharsets.UTF_8);
        JsonNode root = cosmoGet("/bff/v3/gravities?artistId=" + encodedArtist);
        if (!root.path("ongoing").isArray() || !root.path("upcoming").isArray() || !root.path("past").isArray()) {
            throw new IllegalStateException("COSMO 그래비티 목록 응답 형식이 변경되었습니다.");
        }
        List<Map<String, Object>> result = new ArrayList<>();

        appendGravityGroup(result, root.path("ongoing"), "진행 중", artistId);
        appendGravityGroup(result, root.path("upcoming"), "예정", artistId);
        appendGravityGroup(result, root.path("past"), "종료", artistId);

        return result;
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

    private void requireCosmoToken() {
        if (!hasCosmoToken()) throw new IllegalStateException("COSMO 인증이 아직 연결되지 않았습니다.");
    }

    public static String safeImage(String url) {
        return url.startsWith("https://") ? url : "";
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return fallback;
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
}
