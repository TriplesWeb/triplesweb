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
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
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
        // cosmo-tui documents /v4/gravities?category=all for the grouped list.
        // Request a page of recent history while retaining upcoming and ongoing groups.
        JsonNode response = cosmoGet("/v4/gravities?category=all&artistId=" + encodedArtist
                + "&skip=0&take=12&sortBy=endDate&sort=desc");
        JsonNode root = response.path("data").isObject() ? response.path("data") : response;
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
            String gravityId = gravityNode.path("id").asText("");
            String title = gravityNode.path("title").asText("Gravity");
            String startDate = firstNonBlank(
                    gravityNode.path("startDate").asText(""),
                    gravityNode.path("entireStartDate").asText(""),
                    gravityNode.path("startAt").asText(""));
            String endDate = firstNonBlank(
                    gravityNode.path("endDate").asText(""),
                    gravityNode.path("entireEndDate").asText(""),
                    gravityNode.path("endAt").asText(""));
            long totalComo = gravityNode.path("result").path("totalComoUsed").asLong(
                    gravityNode.path("totalComoUsed").asLong(0));

            // Align API keys to the field names expected by gravity.mustache.
            gravity.put("id", gravityId);
            gravity.put("number", gravityId);
            gravity.put("title", title);
            gravity.put("status", statusLabel);
            gravity.put("statusLabel", statusLabel);
            gravity.put("season", firstNonBlank(
                    gravityNode.path("dimension").asText(""),
                    gravityNode.path("type").asText(""),
                    artistId));
            gravity.put("startDate", displayDate(startDate));
            gravity.put("endDate", displayDate(endDate));
            gravity.put("description", firstNonBlank(
                    gravityNode.path("description").asText(""),
                    gravityNode.path("summary").asText(""),
                    "상세 설명은 COSMO API에서 제공하지 않습니다."));
            gravity.put("hasComoCount", totalComo > 0);
            gravity.put("comoCount", totalComo > 0
                    ? NumberFormat.getNumberInstance(Locale.US).format(totalComo)
                    : "");
            gravity.put("artistId", artistId);
            gravity.put("apolloUrl", apolloBaseUrl + "/gravity/" + artistId + "/" + gravityId);

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
     * Loads one Gravity record from COSMO. IDs are COSMO Gravity IDs, not poll IDs.
     */
    public JsonNode fetchGravityDetail(long gravityId) {
        requireCosmoToken();
        if (gravityId <= 0) {
            throw new IllegalArgumentException("gravityId must be positive");
        }

        JsonNode response = cosmoGet("/v3/gravities/" + gravityId);
        if (response == null || response.isNull()) {
            throw new IllegalStateException("COSMO returned an empty Gravity response");
        }
        JsonNode data = response.path("data");
        JsonNode gravity = response.path("gravity");
        if (gravity.isObject()) return gravity;
        if (data.isObject()) {
            gravity = data.path("gravity");
            return gravity.isObject() ? gravity : data;
        }
        return response;
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

        JsonNode response = cosmoGet("/v3/polls/" + pollId);
        JsonNode details = response.path("pollDetail");
        if (details.isObject()) return details;

        JsonNode data = response.path("data");
        details = data.path("pollDetail");
        if (details.isObject()) return details;
        if (data.path("choices").isArray()) return data;
        if (response.path("choices").isArray()) return response;
        return null;
    }

    /**
     * Converts the upstream Gravity payload into the field names consumed by the
     * existing Mustache detail template. Missing result fields stay absent rather
     * than being replaced with invented vote counts.
     */
    public Map<String, Object> buildGravityDetailView(JsonNode source) {
        if (source == null || source.isNull() || source.isMissingNode()) {
            throw new IllegalStateException("COSMO returned no Gravity detail");
        }

        JsonNode gravity = source;
        if (source.path("gravity").isObject()) gravity = source.path("gravity");
        else if (source.path("data").isObject()) {
            JsonNode nested = source.path("data");
            gravity = nested.path("gravity").isObject() ? nested.path("gravity") : nested;
        }

        Map<String, Object> view = new LinkedHashMap<>();
        String id = firstText(gravity, "id", "gravityId");
        String title = firstText(gravity, "title", "name");
        String category = firstText(gravity, "type", "pollType");
        String description = firstText(gravity, "description", "summary");
        if (description.isBlank() && gravity.path("body").isArray()) {
            StringBuilder bodyText = new StringBuilder();
            for (JsonNode block : gravity.path("body")) {
                String type = block.path("type").asText("");
                if ("text".equalsIgnoreCase(type) || "heading".equalsIgnoreCase(type)) {
                    String text = block.path("text").asText("");
                    if (!text.isBlank()) {
                        if (bodyText.length() > 0) bodyText.append("\n\n");
                        bodyText.append(text);
                    }
                }
            }
            description = bodyText.toString();
        }

        String startDate = firstNonBlank(
                gravity.path("startDate").asText(""),
                gravity.path("entireStartDate").asText(""),
                "");
        String endDate = firstNonBlank(
                gravity.path("endDate").asText(""),
                gravity.path("entireEndDate").asText(""),
                "");

        List<Map<String, String>> candidates = new ArrayList<>();
        boolean publishedResult = false;
        boolean hasComoTotal = false;
        long totalComo = gravity.path("result").path("totalComoUsed").asLong(0);
        if (totalComo > 0) hasComoTotal = true;

        JsonNode polls = gravity.path("polls");
        if (polls.isArray()) {
            for (JsonNode originalPoll : polls) {
                JsonNode poll = originalPoll;
                JsonNode choices = poll.path("choices");
                if (!choices.isArray() || choices.isEmpty()) {
                    long pollId = poll.path("id").asLong(0);
                    if (pollId > 0 && hasCosmoToken()) {
                        try {
                            JsonNode pollDetails = fetchPollDetails(pollId);
                            if (pollDetails != null && pollDetails.path("choices").isArray()) {
                                choices = pollDetails.path("choices");
                            }
                        } catch (RuntimeException ignored) {
                            // Keep showing the Gravity record even if one poll cannot be enriched.
                        }
                    }
                }

                JsonNode pollResult = poll.path("result");
                JsonNode results = pollResult.path("results");
                boolean hasPollResult = pollResult.isObject() && !pollResult.isEmpty();
                if (hasPollResult) publishedResult = true;
                long pollComo = pollResult.path("totalComoUsed").asLong(0);
                if (pollComo > 0) {
                    // Prefer the aggregate total from the Gravity record when present.
                    // Otherwise sum the published totals across its polls.
                    if (gravity.path("result").path("totalComoUsed").asLong(0) <= 0) {
                        totalComo += pollComo;
                    }
                    hasComoTotal = true;
                }

                int count = Math.max(choices.isArray() ? choices.size() : 0,
                        results.isArray() ? results.size() : 0);
                for (int i = 0; i < count; i++) {
                    JsonNode choice = choices.isArray() && i < choices.size()
                            ? choices.get(i) : objectMapper.createObjectNode();
                    JsonNode result = results.isArray() && i < results.size()
                            ? results.get(i) : objectMapper.createObjectNode();

                    String name = firstNonBlank(
                            firstText(result, "choiceName", "title", "choiceTitle", "candidateTitle", "name"),
                            firstText(choice, "title", "name", "label"),
                            "후보 " + (i + 1));
                    String imageUrl = firstNonBlank(
                            firstText(result, "choiceImageURL", "choiceImageUrl", "imageURL", "imageUrl"),
                            firstText(choice, "txImageUrl", "imageUrl", "imageURL"),
                            "");
                    int rank = result.path("rank").asInt(i + 1);
                    long candidateComo = result.path("comoUsed").asLong(
                            result.path("totalComoUsed").asLong(0));

                    Map<String, String> candidate = new LinkedHashMap<>();
                    candidate.put("number", String.format("%02d", candidates.size() + 1));
                    candidate.put("name", name);
                    candidate.put("type", hasPollResult ? rank + "위" : "투표 후보");
                    candidate.put("amount", candidateComo > 0
                            ? NumberFormat.getNumberInstance(Locale.US).format(candidateComo) + " COMO" : "");
                    candidate.put("imageUrl", imageUrl);
                    candidates.add(candidate);
                }
            }
        }

        List<Map<String, String>> leaderboard = new ArrayList<>();
        JsonNode leaderboardNode = gravity.path("leaderboard");
        if (leaderboardNode.isArray()) {
            for (JsonNode entry : leaderboardNode) {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("rank", firstText(entry, "rank"));
                item.put("nickname", firstText(entry, "nickname", "name"));
                long amount = entry.path("comoUsed").asLong(entry.path("totalComoUsed").asLong(0));
                item.put("como", amount > 0
                        ? NumberFormat.getNumberInstance(Locale.US).format(amount) + " COMO" : "—");
                if (!item.get("nickname").isBlank()) leaderboard.add(item);
            }
        }

        JsonNode overallResult = gravity.path("result");
        String winnerTitle = firstText(overallResult, "resultTitle", "title", "winnerTitle");
        String winnerImageUrl = firstText(overallResult, "resultImageUrl", "resultImageURL", "imageUrl");

        String status = firstText(gravity, "status", "state");
        if (status.isBlank()) {
            status = publishedResult ? "결과 공개" : "COSMO API 조회 완료";
        }
        String como = hasComoTotal
                ? NumberFormat.getNumberInstance(Locale.US).format(totalComo) + " COMO"
                : "결과 미공개";
        String resultNote = publishedResult
                ? "공개된 후보 결과는 COSMO API 응답을 기준으로 표시합니다."
                : "COSMO API에서 최종 결과가 공개되기 전이거나 결과 데이터가 제공되지 않았습니다. 확인 가능한 후보 정보만 표시합니다.";

        view.put("id", firstNonBlank(id, "-", "-"));
        view.put("title", firstNonBlank(title, "그래비티 상세 정보", "그래비티 상세 정보"));
        view.put("category", firstNonBlank(category, "COSMO GRAVITY", "COSMO GRAVITY"));
        view.put("status", status);
        view.put("description", firstNonBlank(description, "상세 설명을 제공하지 않습니다.", "상세 설명을 제공하지 않습니다."));
        view.put("period", firstNonBlank(displayDate(startDate), "-", "-") + " ~ "
                + firstNonBlank(displayDate(endDate), "-", "-"));
        view.put("como", como);
        view.put("resultNote", resultNote);
        view.put("winnerTitle", winnerTitle);
        view.put("winnerImageUrl", winnerImageUrl);
        view.put("hasWinner", !winnerTitle.isBlank());
        view.put("candidates", candidates);
        view.put("hasCandidates", !candidates.isEmpty());
        view.put("candidateCount", candidates.size());
        view.put("leaderboard", leaderboard);
        view.put("hasLeaderboard", !leaderboard.isEmpty());
        view.put("bannerImageUrl", firstText(gravity, "bannerImageUrl", "bannerImageURL"));
        return view;
    }

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

    private static String firstText(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText("");
                if (!text.isBlank()) return text;
            }
        }
        return "";
    }

    private static String displayDate(String value) {
        if (value == null || value.isBlank()) return "";
        String date = value.trim();
        int timeSeparator = date.indexOf('T');
        if (timeSeparator > 0) date = date.substring(0, timeSeparator);
        if (date.matches("\\d{4}-\\d{2}-\\d{2}")) return date.replace('-', '.');
        return date;
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
