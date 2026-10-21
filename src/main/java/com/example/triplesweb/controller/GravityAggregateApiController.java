package com.example.triplesweb.controller;

import com.example.triplesweb.service.GravityService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.List;

/**
 * Read-only proxy for Apollo's public Gravity aggregation endpoint.
 * This endpoint does not require a COSMO access token; it only needs a COSMO Poll ID.
 */
@RestController
@RequestMapping("/api/cosmo/gravity")
public class GravityAggregateApiController {

    private final GravityService gravityService;
    private final String tripleSPollMap;

    public GravityAggregateApiController(
            GravityService gravityService,
            @Value("${gravity.apollo-triples-poll-map:}") String tripleSPollMap
    ) {
        this.gravityService = gravityService;
        this.tripleSPollMap = tripleSPollMap == null ? "" : tripleSPollMap;
    }

    @GetMapping("/aggregate")
    public ResponseEntity<?> aggregate(
            @RequestParam long pollId,
            @RequestParam(required = false) Long gravityId
    ) {
        if (pollId <= 0 || (gravityId != null && gravityId <= 0)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Gravity ID와 Poll ID는 1 이상의 숫자여야 합니다."));
        }

        try {
            if (!isVerifiedTripleSPoll(pollId, gravityId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", "이 페이지는 tripleS 그래비티 전용입니다. tripleS 소속이 확인된 Poll ID만 조회할 수 있습니다."
                ));
            }
            JsonNode data = gravityService.fetchAggregatedData(pollId);
            return ResponseEntity.ok(data);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "Apollo에서 해당 Poll ID를 찾지 못했습니다. Gravity ID와 Poll ID가 서로 다를 수 있으니 Apollo 상세 페이지의 Poll ID를 확인해 주세요."
                ));
            }
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "Apollo 공개 집계 API가 HTTP " + exception.getStatusCode().value() + " 응답을 반환했습니다."
            ));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", GravityService.describeApiError(exception)
            ));
        }
    }

    /**
     * Only allow Poll IDs explicitly mapped to tripleS Gravity IDs, or Poll IDs
     * found in COSMO's tripleS-only Gravity list when a COSMO token is configured.
     */
    private boolean isVerifiedTripleSPoll(long pollId, Long gravityId) {
        if (isConfiguredTripleSPair(pollId, gravityId)) return true;

        if (!gravityService.hasCosmoToken()) return false;
        try {
            List<Map<String, Object>> gravities = gravityService.fetchGravityList("tripleS");
            for (Map<String, Object> gravity : gravities) {
                String id = String.valueOf(gravity.getOrDefault("id", ""));
                if (gravityId != null && !gravityId.toString().equals(id)) continue;
                Object pollsObject = gravity.get("polls");
                if (!(pollsObject instanceof List<?> polls)) continue;
                for (Object pollObject : polls) {
                    if (!(pollObject instanceof Map<?, ?> poll)) continue;
                    if (matchesPollId(poll.get("id"), pollId)
                            || matchesPollId(poll.get("cosmoId"), pollId)) {
                        return true;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Fail closed: a failed COSMO ownership check must not expose another artist's poll.
        }
        return false;
    }

    private boolean isConfiguredTripleSPair(long pollId, Long gravityId) {
        if (tripleSPollMap.isBlank()) return false;
        for (String entry : tripleSPollMap.split(",")) {
            String[] pair = entry.trim().split(":", 2);
            if (pair.length != 2) continue;
            try {
                long configuredGravityId = Long.parseLong(pair[0].trim());
                long configuredPollId = Long.parseLong(pair[1].trim());
                if (configuredPollId != pollId) continue;
                if (gravityId == null || gravityId == configuredGravityId) return true;
            } catch (NumberFormatException ignored) {
                // Ignore invalid entries; do not loosen the allowlist.
            }
        }
        return false;
    }

    private static boolean matchesPollId(Object value, long pollId) {
        if (value == null) return false;
        try {
            return Long.parseLong(String.valueOf(value)) == pollId;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
