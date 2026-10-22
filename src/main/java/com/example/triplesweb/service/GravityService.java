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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * COSMO Gravity integration for the tripleS community.
 *
 * Rules:
 * - This service only loads tripleS Gravity records.
 * - Gravity IDs and COSMO Poll IDs are separate values.
 * - The list page loads metadata only; it does not call Apollo's aggregate API.
 * - Aggregation is an optional detail-page operation and is only used for a
 *   COSMO Poll ID that was found in the tripleS Gravity payload.
 */
@Service
public class GravityService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() {};

    private static final String TRIPLES_ARTIST_ID = "tripleS";
    private static final int PAGE_SIZE = 12;
    private static final int MAX_PAGES = 100;
    private static final long GRAVITY_CACHE_MILLIS = 10 * 60 * 1000L;
    private static final long POLL_MAPPING_CACHE_MILLIS = 60 * 60 * 1000L;

    private final RestClient http;
    private final ObjectMapper mapper;
    private final String apolloBaseUrl;
    private final String cosmoApiBaseUrl;
    private final String cosmoAccessToken;

    private volatile long gravityCacheTime;
    private volatile List<Map<String, Object>> gravityCache = List.of();
    private volatile long pollCacheTime;
    private volatile List<Map<String, Object>> pollMappingCache = List.of();

    public GravityService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${gravity.apollo-base-url:https://apollo.cafe}") String apolloBaseUrl,
            @Value("${gravity.cosmo-api-base-url:https://api.cosmo.fans}") String cosmoApiBaseUrl,
            @Value("${gravity.cosmo-access-token:}") String cosmoAccessToken
    ) {
        this.http = restClientBuilder.build();
        this.mapper = objectMapper;
        this.apolloBaseUrl = trimSlash(apolloBaseUrl);
        this.cosmoApiBaseUrl = trimSlash(cosmoApiBaseUrl);
        this.cosmoAccessToken = cosmoAccessToken == null ? "" : cosmoAccessToken.trim();
    }

    public boolean hasCosmoToken() {
        return !cosmoAccessToken.isBlank();
    }

    /**
     * Returns all available tripleS Gravity records, deduplicated by Gravity ID.
     * The artist parameter is intentionally ignored: this page is tripleS-only.
     */
    public List<Map<String, Object>> fetchGravityList(String ignoredArtistId) {
        requireCosmoToken();

        long now = System.currentTimeMillis();
        if (gravityCacheTime > 0 && now - gravityCacheTime < GRAVITY_CACHE_MILLIS) {
            return copyMaps(gravityCache);
        }

        synchronized (this) {
            now = System.currentTimeMillis();
            if (gravityCacheTime > 0 && now - gravityCacheTime < GRAVITY_CACHE_MILLIS) {
                return copyMaps(gravityCache);
            }

            List<Map<String, Object>> loaded = loadGravityPages();
            gravityCache = copyMaps(loaded);
            gravityCacheTime = System.currentTimeMillis();
            // The Poll mapping cache is derived from the same upstream records.
            pollMappingCache = buildPollMappings(loaded);
            pollCacheTime = gravityCacheTime;
            return copyMaps(loaded);
        }
    }

    private List<Map<String, Object>> loadGravityPages() {
        String artist = UriUtils.encodeQueryParam(TRIPLES_ARTIST_ID, StandardCharsets.UTF_8);
        Map<String, Map<String, Object>> byGravityId = new LinkedHashMap<>();

        for (int page = 0; page < MAX_PAGES; page++) {
            int skip = page * PAGE_SIZE;
            JsonNode response = cosmoGet(
                    "/v4/gravities?category=all&artistId=" + artist
                            + "&skip=" + skip + "&take=" + PAGE_SIZE
                            + "&sortBy=endDate&sort=desc"
            );
            JsonNode root = unwrapData(response);
            int recordsInPage = 0;
            int newlyAdded = 0;

            // COSMO groups the response by status. Deduplicate the same Gravity
            // if it appears in more than one group.
            for (String groupName : List.of("ongoing", "upcoming", "past")) {
                JsonNode group = root.path(groupName);
                if (!group.isArray()) {
                    continue;
                }

                recordsInPage += group.size();
                for (JsonNode node : group) {
                    String gravityId = firstText(node, "id", "gravityId");
                    if (gravityId.isBlank()) {
                        continue;
                    }
                    if (!byGravityId.containsKey(gravityId)) {
                        byGravityId.put(gravityId, toGravityListItem(node, gravityId));
                        newlyAdded++;
                    }
                }
            }

            // Stop if the endpoint is empty or ignored the pagination offset.
            if (recordsInPage == 0 || newlyAdded == 0) {
                break;
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(byGravityId.values());
        result.sort(Comparator.comparing(
                item -> String.valueOf(item.getOrDefault("startSort", "")),
                Comparator.reverseOrder()
        ));
        for (Map<String, Object> item : result) {
            item.remove("startSort");
        }
        return result;
    }

    private Map<String, Object> toGravityListItem(JsonNode node, String gravityId) {
        Map<String, Object> item = mapper.convertValue(node, MAP_TYPE);

        String title = firstNonBlank(firstText(node, "title", "name"), "Gravity");
        String start = firstNonBlank(
                firstText(node, "startDate", "entireStartDate", "startAt"), "");
        String end = firstNonBlank(
                firstText(node, "endDate", "entireEndDate", "endAt"), "");
        String status = statusLabel(node, end);
        long totalComo = node.path("result").path("totalComoUsed")
                .asLong(node.path("totalComoUsed").asLong(0));

        item.put("id", gravityId);
        item.put("gravityId", gravityId);
        item.put("number", gravityId);
        item.put("title", title);
        item.put("status", status);
        item.put("statusLabel", status);
        item.put("season", firstNonBlank(firstText(node, "dimension", "type"), "tripleS"));
        item.put("startDate", displayDate(start));
        item.put("endDate", displayDate(end));
        item.put("startSort", start);
        item.put("description", firstNonBlank(
                firstText(node, "description", "summary"),
                "상세 설명이 제공되지 않습니다."
        ));
        item.put("artistId", TRIPLES_ARTIST_ID);
        item.put("hasComoCount", totalComo > 0);
        item.put("comoCount", totalComo > 0
                ? NumberFormat.getNumberInstance(Locale.US).format(totalComo) : "");
        item.put("apolloUrl", apolloBaseUrl + "/gravity/triples/" + gravityId);

        List<Map<String, Object>> polls = new ArrayList<>();
        JsonNode pollNodes = node.path("polls");
        if (pollNodes.isArray()) {
            for (JsonNode pollNode : pollNodes) {
                long pollId = pollNode.path("id").asLong(
                        pollNode.path("cosmoId").asLong(0));
                if (pollId <= 0) {
                    continue;
                }

                Map<String, Object> poll = mapper.convertValue(pollNode, MAP_TYPE);
                poll.put("pollId", pollId);
                poll.put("gravityId", gravityId);
                poll.put("artistId", TRIPLES_ARTIST_ID);
                poll.put("gravityTitle", title);
                poll.put("pollTitle", firstNonBlank(
                        firstText(pollNode, "title", "titleKo"), title));
                poll.put("apolloUrl", apolloBaseUrl + "/gravity/triples/" + gravityId
                        + "?poll=" + pollId);
                polls.add(poll);
            }
        }
        item.put("polls", polls);
        item.put("hasPolls", !polls.isEmpty());
        item.put("pollCount", polls.size());
        return item;
    }

    /**
     * Poll mappings are extracted from COSMO's actual Gravity payload.
     * Never infer a Poll ID from a Gravity ID or from an assumed numeric range.
     */
    public List<Map<String, Object>> fetchAllTripleSPollMappings() {
        requireCosmoToken();
        long now = System.currentTimeMillis();
        if (pollCacheTime > 0 && now - pollCacheTime < POLL_MAPPING_CACHE_MILLIS) {
            return copyMaps(pollMappingCache);
        }

        synchronized (this) {
            now = System.currentTimeMillis();
            if (pollCacheTime > 0 && now - pollCacheTime < POLL_MAPPING_CACHE_MILLIS) {
                return copyMaps(pollMappingCache);
            }
            List<Map<String, Object>> mappings = buildPollMappings(loadGravityPages());
            pollMappingCache = copyMaps(mappings);
            pollCacheTime = System.currentTimeMillis();
            return copyMaps(mappings);
        }
    }

    private List<Map<String, Object>> buildPollMappings(List<Map<String, Object>> gravities) {
        Map<Long, Map<String, Object>> mappings = new LinkedHashMap<>();
        for (Map<String, Object> gravity : gravities) {
            Object rawPolls = gravity.get("polls");
            if (!(rawPolls instanceof List<?> polls)) {
                continue;
            }
            for (Object rawPoll : polls) {
                if (!(rawPoll instanceof Map<?, ?> poll)) {
                    continue;
                }
                long pollId = asLong(poll.get("pollId"), asLong(poll.get("id"), 0));
                long gravityId = asLong(gravity.get("gravityId"), asLong(gravity.get("id"), 0));
                if (pollId <= 0 || gravityId <= 0) {
                    continue;
                }

                Map<String, Object> mapping = new LinkedHashMap<>();
                mapping.put("pollId", pollId);
                mapping.put("gravityId", gravityId);
                mapping.put("pollTitle", firstNonBlank(
                        stringValue(poll.get("pollTitle")), stringValue(gravity.get("title"))));
                mapping.put("gravityTitle", stringValue(gravity.get("title")));
                mapping.put("artistId", TRIPLES_ARTIST_ID);
                mapping.put("startDate", stringValue(poll.get("startDate")));
                mapping.put("endDate", stringValue(poll.get("endDate")));
                mapping.put("apolloUrl", apolloBaseUrl + "/gravity/triples/" + gravityId
                        + "?poll=" + pollId);
                mappings.put(pollId, mapping);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(mappings.values());
        result.sort(Comparator.comparingLong(row -> asLong(row.get("pollId"), 0)));
        return result;
    }

    public Map<String, Object> findTripleSPollMapping(long pollId) {
        if (pollId <= 0) {
            return null;
        }
        for (Map<String, Object> mapping : fetchAllTripleSPollMappings()) {
            if (asLong(mapping.get("pollId"), 0) == pollId) {
                return new LinkedHashMap<>(mapping);
            }
        }
        return null;
    }

    public Long findTripleSPollIdForGravity(long gravityId) {
        if (gravityId <= 0) {
            return null;
        }
        for (Map<String, Object> mapping : fetchAllTripleSPollMappings()) {
            if (asLong(mapping.get("gravityId"), 0) == gravityId) {
                return asLong(mapping.get("pollId"), 0);
            }
        }
        return null;
    }

    /** Fetches one COSMO Gravity record. The argument is a Gravity ID. */
    public JsonNode fetchGravityDetail(long gravityId) {
        requireCosmoToken();
        requirePositiveId(gravityId, "gravityId");
        JsonNode response = cosmoGet("/v3/gravities/" + gravityId);
        JsonNode gravity = unwrapData(response);
        if (gravity == null || gravity.isNull() || gravity.isMissingNode()) {
            throw new IllegalStateException("COSMO returned no Gravity detail");
        }
        return gravity;
    }

    /**
     * Fetches public Apollo aggregate data for a COSMO Poll ID.
     * Call this only from the detail page after verifying that the Poll ID is
     * present in the tripleS mapping.
     */
    public JsonNode fetchAggregatedData(long pollId) {
        requirePositiveId(pollId, "pollId");
        if (findTripleSPollMapping(pollId) == null) {
            throw new IllegalArgumentException("tripleS 소속이 확인된 Poll ID가 아닙니다.");
        }

        JsonNode response = http.get()
                .uri(apolloBaseUrl + "/api/gravity/" + pollId + "/aggregated")
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.isNull() || response.has("error")) {
            throw new IllegalStateException("Apollo 집계 API가 유효한 결과를 반환하지 않았습니다.");
        }
        return response;
    }

    /** Fetches candidate metadata by COSMO Poll ID, not Gravity ID. */
    public JsonNode fetchPollDetails(long pollId) {
        requireCosmoToken();
        requirePositiveId(pollId, "pollId");
        if (findTripleSPollMapping(pollId) == null) {
            throw new IllegalArgumentException("tripleS 소속이 확인된 Poll ID가 아닙니다.");
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
     * Adapts a COSMO response to the fields used by the existing detail template.
     * Candidate counts are displayed only when the upstream response actually
     * contains published results.
     */
    public Map<String, Object> buildGravityDetailView(JsonNode source) {
        if (source == null || source.isNull() || source.isMissingNode()) {
            throw new IllegalStateException("COSMO returned no Gravity detail");
        }

        JsonNode gravity = unwrapData(source);
        Map<String, Object> view = new LinkedHashMap<>();

        String gravityId = firstText(gravity, "id", "gravityId");
        String title = firstNonBlank(firstText(gravity, "title", "name"), "그래비티 상세 정보");
        String description = firstNonBlank(
                firstText(gravity, "description", "summary"), "");
        if (description.isBlank() && gravity.path("body").isArray()) {
            List<String> blocks = new ArrayList<>();
            for (JsonNode block : gravity.path("body")) {
                if ("text".equalsIgnoreCase(block.path("type").asText(""))
                        || "heading".equalsIgnoreCase(block.path("type").asText(""))) {
                    String text = block.path("text").asText("");
                    if (!text.isBlank()) blocks.add(text);
                }
            }
            description = String.join("\n\n", blocks);
        }

        String start = firstText(gravity, "startDate", "entireStartDate", "startAt");
        String end = firstText(gravity, "endDate", "entireEndDate", "endAt");
        JsonNode overallResult = gravity.path("result");
        long totalComo = overallResult.path("totalComoUsed")
                .asLong(gravity.path("totalComoUsed").asLong(0));

        List<Map<String, Object>> candidates = new ArrayList<>();
        boolean hasPublishedResults = false;
        JsonNode polls = gravity.path("polls");
        if (polls.isArray()) {
            for (JsonNode poll : polls) {
                JsonNode result = poll.path("result");
                JsonNode results = result.path("results");
                JsonNode choices = poll.path("choices");

                if (result.isObject() && !result.isEmpty()
                        && results.isArray() && !results.isEmpty()) {
                    hasPublishedResults = true;
                }

                // Use only data embedded in the Gravity response here. Poll
                // details can be loaded separately by the detail controller.
                int size = Math.max(
                        choices.isArray() ? choices.size() : 0,
                        results.isArray() ? results.size() : 0
                );
                for (int i = 0; i < size; i++) {
                    JsonNode choice = choices.isArray() && i < choices.size()
                            ? choices.get(i) : mapper.createObjectNode();
                    JsonNode resultRow = results.isArray() && i < results.size()
                            ? results.get(i) : mapper.createObjectNode();

                    Map<String, Object> candidate = new LinkedHashMap<>();
                    candidate.put("number", String.format("%02d", candidates.size() + 1));
                    candidate.put("name", firstNonBlank(
                            firstText(resultRow, "choiceName", "title", "choiceTitle", "name"),
                            firstText(choice, "title", "name", "label"),
                            "후보 " + (i + 1)));
                    candidate.put("type", hasPublishedResults
                            ? resultRow.path("rank").asText("") + "위" : "투표 후보");
                    long amount = resultRow.path("comoUsed")
                            .asLong(resultRow.path("totalComoUsed").asLong(-1));
                    candidate.put("amount", amount >= 0 && hasPublishedResults
                            ? NumberFormat.getNumberInstance(Locale.US).format(amount) + " COMO" : "");
                    candidate.put("imageUrl", firstText(
                            resultRow, "choiceImageURL", "choiceImageUrl", "imageURL", "imageUrl"));
                    if (candidate.get("imageUrl").toString().isBlank()) {
                        candidate.put("imageUrl", firstText(choice, "txImageUrl", "imageUrl", "imageURL"));
                    }
                    candidates.add(candidate);
                }
                if (totalComo <= 0) {
                    totalComo += result.path("totalComoUsed").asLong(0);
                }
            }
        }

        List<Map<String, Object>> leaderboard = new ArrayList<>();
        JsonNode leaderboardNode = gravity.path("leaderboard");
        if (leaderboardNode.isArray()) {
            for (JsonNode row : leaderboardNode) {
                String nickname = firstText(row, "nickname", "name");
                if (nickname.isBlank()) continue;
                long amount = row.path("comoUsed").asLong(row.path("totalComoUsed").asLong(-1));
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("rank", firstText(row, "rank"));
                item.put("nickname", nickname);
                item.put("como", amount >= 0
                        ? NumberFormat.getNumberInstance(Locale.US).format(amount) + " COMO" : "—");
                leaderboard.add(item);
            }
        }

        String winnerTitle = firstText(overallResult, "resultTitle", "title", "winnerTitle");
        String winnerImage = firstText(overallResult, "resultImageUrl", "resultImageURL", "imageUrl");

        view.put("id", gravityId);
        view.put("gravityId", gravityId);
        view.put("title", title);
        view.put("category", firstNonBlank(firstText(gravity, "type", "pollType"), "COSMO GRAVITY"));
        view.put("status", statusLabel(gravity, end));
        view.put("description", firstNonBlank(description, "상세 설명을 제공하지 않습니다."));
        view.put("period", firstNonBlank(displayDate(start), "-") + " ~ " + firstNonBlank(displayDate(end), "-"));
        view.put("como", totalComo > 0
                ? NumberFormat.getNumberInstance(Locale.US).format(totalComo) + " COMO" : "결과 미공개");
        view.put("resultNote", hasPublishedResults
                ? "공개된 결과 데이터가 있습니다."
                : "아직 최종 결과가 공개되지 않았거나 API에서 결과를 제공하지 않습니다.");
        view.put("winnerTitle", winnerTitle);
        view.put("winnerImageUrl", winnerImage);
        view.put("hasWinner", !winnerTitle.isBlank());
        view.put("candidates", candidates);
        view.put("hasCandidates", !candidates.isEmpty());
        view.put("candidateCount", candidates.size());
        view.put("leaderboard", leaderboard);
        view.put("hasLeaderboard", !leaderboard.isEmpty());
        view.put("bannerImageUrl", firstText(gravity, "bannerImageUrl", "bannerImageURL"));
        return view;
    }

    /**
     * Joins candidate metadata with Apollo's finalized COMO totals.
     * The API's finalized.comoPerCandidate is indexed by candidate ID; it is not
     * a live vote count and is intentionally omitted until finalized exists.
     */
    public List<Map<String, Object>> buildCandidateResults(JsonNode pollDetails, JsonNode aggregate) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (pollDetails == null || aggregate == null) return result;

        JsonNode finalized = aggregate.path("finalized");
        if (!finalized.isObject() || finalized.isNull()) return result;

        JsonNode choices = pollDetails.path("choices");
        JsonNode amounts = finalized.path("comoPerCandidate");
        if (!choices.isArray() || !amounts.isArray()) return result;

        for (int index = 0; index < choices.size(); index++) {
            JsonNode choice = choices.get(index);
            if (index >= amounts.size()) continue;

            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("candidateId", index);
            candidate.put("name", firstNonBlank(
                    firstText(choice, "title", "name", "label"), "후보 " + (index + 1)));
            candidate.put("comoAmount", amounts.get(index).asLong(0));
            candidate.put("imageUrl", firstText(choice, "txImageUrl", "imageUrl", "imageURL"));
            result.add(candidate);
        }

        result.sort((left, right) -> Long.compare(
                asLong(right.get("comoAmount"), 0), asLong(left.get("comoAmount"), 0)));
        return result;
    }

    public Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        return mapper.convertValue(node, MAP_TYPE);
    }

    private JsonNode cosmoGet(String path) {
        requireCosmoToken();
        return http.get()
                .uri(cosmoApiBaseUrl + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cosmoAccessToken)
                .retrieve()
                .body(JsonNode.class);
    }

    private static JsonNode unwrapData(JsonNode response) {
        if (response == null || response.isNull() || response.isMissingNode()) return response;
        JsonNode data = response.path("data");
        if (data.isObject()) {
            JsonNode gravity = data.path("gravity");
            return gravity.isObject() ? gravity : data;
        }
        JsonNode gravity = response.path("gravity");
        return gravity.isObject() ? gravity : response;
    }

    private void requireCosmoToken() {
        if (!hasCosmoToken()) {
            throw new IllegalStateException(
                    "COSMO_ACCESS_TOKEN이 설정되지 않았습니다. 실행 환경변수에 토큰을 설정해 주세요.");
        }
    }

    private static void requirePositiveId(long id, String name) {
        if (id <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    private static String statusLabel(JsonNode node, String endDate) {
        String status = firstText(node, "status", "state");
        if (!status.isBlank()) {
            String lower = status.toLowerCase(Locale.ROOT);
            if (lower.contains("ongoing") || lower.contains("active") || lower.contains("progress")) return "진행 중";
            if (lower.contains("upcoming") || lower.contains("scheduled")) return "예정";
            if (lower.contains("past") || lower.contains("ended") || lower.contains("complete")) return "종료";
        }
        return endDate == null || endDate.isBlank() ? "상태 확인 중" : "종료";
    }

    private static List<Map<String, Object>> copyMaps(List<Map<String, Object>> source) {
        List<Map<String, Object>> copy = new ArrayList<>();
        if (source != null) {
            for (Map<String, Object> row : source) {
                if (row != null) copy.add(new LinkedHashMap<>(row));
            }
        }
        return copy;
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

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String displayDate(String value) {
        if (value == null || value.isBlank()) return "";
        String date = value.trim();
        int separator = date.indexOf('T');
        if (separator > 0) date = date.substring(0, separator);
        if (date.matches("\\d{4}-\\d{2}-\\d{2}")) return date.replace('-', '.');
        return date;
    }

    private static String trimSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private static long asLong(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text) {
            try { return Long.parseLong(text); } catch (NumberFormatException ignored) { return fallback; }
        }
        return fallback;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static String describeApiError(Exception exception) {
        if (exception instanceof RestClientResponseException responseException) {
            return "외부 API 응답 오류 (HTTP " + responseException.getStatusCode().value()
                    + "). 토큰 권한, API 경로 또는 응답 형식을 확인해 주세요.";
        }
        if (exception instanceof IllegalStateException) return exception.getMessage();
        if (exception instanceof IllegalArgumentException) return "ID 또는 요청 값이 올바르지 않습니다.";
        return "외부 API 연결에 실패했습니다. 네트워크와 API 접근 정책을 확인해 주세요.";
    }
}
