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
    private final String apolloBaseUrl;
    private final String cosmoApiBaseUrl;
    private final String cosmoAccessToken;

    public GravityService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${gravity.apollo-base-url:https://apollo.cafe}") String apolloBaseUrl,
            @Value("${gravity.cosmo-api-base-url:https://api.cosmo.fans}") String cosmoApiBaseUrl,
            @Value("${gravity.cosmo-access-token:}") String cosmoAccessToken
    ) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.apolloBaseUrl = trimTrailingSlash(apolloBaseUrl);
        this.cosmoApiBaseUrl = trimTrailingSlash(cosmoApiBaseUrl);
        this.cosmoAccessToken = cosmoAccessToken == null ? "" : cosmoAccessToken.trim();
    }

    public boolean hasCosmoToken() {
        return !cosmoAccessToken.isBlank();
    }

    /**
     * Loads Gravity metadata from COSMO. Requires a valid, authorized access token.
     * The API response has upcoming, ongoing and past arrays.
     */
    public List<Map<String, Object>> fetchGravityList(String artistId) {
        requireCosmoToken();

        String encodedArtist = UriUtils.encodeQueryParam(artistId, StandardCharsets.UTF_8);
        JsonNode root = cosmoGet("/bff/v3/gravities?artistId=" + encodedArtist);
        List<Map<String, Object>> result = new ArrayList<>();

        appendGravityGroup(result, root.path("ongoing"), "진행 중", artistId, false);
        appendGravityGroup(result, root.path("upcoming"), "예정", artistId, false);
        appendGravityGroup(result, root.path("past"), "종료", artistId, true);

        return result;
    }

    private void appendGravityGroup(
            List<Map<String, Object>> destination,
            JsonNode group,
            String statusLabel,
            String artistId,
            boolean past
    ) {
        if (!group.isArray()) {
            return;
        }

        int pastCount = 0;
        for (JsonNode gravityNode : group) {
            // Keep the page compact: only show the 12 most recent past entries.
            if (past && pastCount >= 12) {
                break;
            }

            Map<String, Object> gravity = objectMapper.convertValue(gravityNode, MAP_TYPE);
            gravity.put("statusLabel", statusLabel);
            gravity.put("artistId", artistId);
            gravity.put("apolloUrl", apolloBaseUrl + "/gravity/" + artistId + "/" +
                    gravityNode.path("id").asText());

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
            if (past) {
                pastCount++;
            }
        }
    }

    /**
     * Reads the public Apollo aggregate endpoint. The live deployment may enforce
     * access restrictions even though the route in the public source has no
     * explicit API-key check.
     */
    public JsonNode fetchAggregatedData(long pollId) {
        if (pollId <= 0) {
            throw new IllegalArgumentException("pollId must be positive");
        }

        String url = apolloBaseUrl + "/api/gravity/" + pollId + "/aggregated";
        JsonNode response = restClient.get()
                .uri(url)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.has("error")) {
            throw new IllegalStateException("Apollo returned an empty or error response");
        }
        return response;
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
        return details.isMissingNode() || details.isNull() ? null : details;
    }

    /**
     * Maps final candidate totals to COSMO choice titles. The upstream aggregate
     * exposes candidate totals only after all votes have been revealed.
     */
    public List<Map<String, Object>> buildCandidateResults(JsonNode pollDetails, JsonNode aggregate) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (pollDetails == null || aggregate == null) {
            return result;
        }

        JsonNode finalized = aggregate.path("finalized");
        if (finalized.isMissingNode() || finalized.isNull()) {
            return result;
        }

        JsonNode choices = pollDetails.path("choices");
        JsonNode amounts = finalized.path("comoPerCandidate");
        if (!choices.isArray()) {
            return result;
        }

        for (int index = 0; index < choices.size(); index++) {
            JsonNode choice = choices.get(index);
            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("candidateId", index);
            candidate.put("name", firstNonBlank(
                    choice.path("title").asText(""),
                    choice.path("id").asText(""),
                    "후보 " + (index + 1)
            ));
            candidate.put("comoAmount", amounts.path(index).asLong(0));
            candidate.put("imageUrl", firstNonBlank(
                    choice.path("txImageUrl").asText(""),
                    choice.path("imageUrl").asText(""),
                    ""
            ));
            result.add(candidate);
        }

        result.sort((a, b) -> Long.compare(
                ((Number) b.get("comoAmount")).longValue(),
                ((Number) a.get("comoAmount")).longValue()
        ));
        return result;
    }

    public Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return objectMapper.convertValue(node, MAP_TYPE);
    }

    private JsonNode cosmoGet(String path) {
        requireCosmoToken();
        return restClient.get()
                .uri(cosmoApiBaseUrl + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cosmoAccessToken)
                .retrieve()
                .body(JsonNode.class);
    }

    private void requireCosmoToken() {
        if (!hasCosmoToken()) {
            throw new IllegalStateException(
                    "COSMO_ACCESS_TOKEN is not configured. Public aggregate lookup can still be attempted by poll ID."
            );
        }
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
