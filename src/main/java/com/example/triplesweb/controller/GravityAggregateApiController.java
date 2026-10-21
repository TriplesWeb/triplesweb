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
 * Read-only proxy for Apollo's public aggregation endpoint.
 * Only Poll IDs verified as belonging to tripleS are allowed; an unverified ID fails closed.
 */
@RestController
@RequestMapping("/api/cosmo/gravity")
public class GravityAggregateApiController {

    private final GravityService gravityService;
    private final String tripleSPollMap;
    private final String tripleSPollIds;

    public GravityAggregateApiController(
            GravityService gravityService,
            @Value("${gravity.apollo-triples-poll-map:}") String tripleSPollMap,
            @Value("${gravity.apollo-triples-poll-ids:267}") String tripleSPollIds
    ) {
        this.gravityService = gravityService;
        this.tripleSPollMap = tripleSPollMap == null ? "" : tripleSPollMap;
        this.tripleSPollIds = tripleSPollIds == null ? "" : tripleSPollIds;
    }

    @GetMapping("/discover")
    public ResponseEntity<?> discoverTripleSPolls() {
        try {
            List<Map<String, Object>> polls = gravityService.discoverTripleSPollAggregates();
            return ResponseEntity.ok(Map.of(
                    "scannedRange", "1-264",
                    "count", polls.size(),
                    "polls", polls
            ));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", GravityService.describeApiError(exception)
            ));
        }
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
            Long resolvedPollId = resolveVerifiedTripleSPollId(pollId, gravityId);
            if (resolvedPollId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", "이 페이지는 tripleS 그래비티 전용입니다. tripleS 소속이 확인된 Gravity ID 또는 Poll ID만 조회할 수 있습니다."
                ));
            }
            JsonNode data = gravityService.fetchAggregatedData(resolvedPollId);
            return ResponseEntity.ok()
                    .header("X-Apollo-Poll-Id", String.valueOf(resolvedPollId))
                    .body(data);
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
     * Accept a verified tripleS Gravity ID or Poll ID.
     * Gravity IDs are translated to their Apollo aggregate Poll IDs when a mapping exists.
     */
    private Long resolveVerifiedTripleSPollId(long requestedId, Long gravityId) {
        if (gravityId != null) {
            if (isConfiguredTripleSPair(requestedId, gravityId)) return requestedId;
            try {
                Map<String, Object> mapping = gravityService.findTripleSPollMapping(requestedId);
                if (mapping != null
                        && ((Number) mapping.get("gravityId")).longValue() == gravityId) {
                    return requestedId;
                }
            } catch (RuntimeException ignored) {
                // Fall through to the explicitly configured mapping only.
            }
            return null;
        }

        // Keep known manual aliases working, then use COSMO's complete tripleS mapping.
        Long configuredPollId = configuredPollForGravityId(requestedId);
        if (configuredPollId != null) return configuredPollId;
        if (isConfiguredTripleSPollId(requestedId) || isConfiguredPollId(requestedId)) return requestedId;

        if (gravityService.hasCosmoToken()) {
            try {
                Long pollForGravity = gravityService.findTripleSPollIdForGravity(requestedId);
                if (pollForGravity != null) return pollForGravity;

                if (gravityService.findTripleSPollMapping(requestedId) != null) {
                    return requestedId;
                }
            } catch (RuntimeException ignored) {
                // Fail closed if COSMO cannot confirm that this belongs to tripleS.
            }
        }
        return null;
    }

    private Long configuredPollForGravityId(long gravityId) {
        if (tripleSPollMap.isBlank()) return null;
        for (String entry : tripleSPollMap.split(",")) {
            String[] pair = entry.trim().split(":", 2);
            if (pair.length != 2) continue;
            try {
                long configuredGravityId = Long.parseLong(pair[0].trim());
                long configuredPollId = Long.parseLong(pair[1].trim());
                if (configuredGravityId == gravityId) return configuredPollId;
            } catch (NumberFormatException ignored) {
                // Ignore malformed mapping entries.
            }
        }
        return null;
    }

    private boolean isConfiguredPollId(long pollId) {
        if (tripleSPollMap.isBlank()) return false;
        for (String entry : tripleSPollMap.split(",")) {
            String[] pair = entry.trim().split(":", 2);
            if (pair.length != 2) continue;
            try {
                if (Long.parseLong(pair[1].trim()) == pollId) return true;
            } catch (NumberFormatException ignored) {
                // Ignore malformed mapping entries.
            }
        }
        return false;
    }

    private boolean isConfiguredTripleSPollId(long pollId) {
        for (String value : tripleSPollIds.split(",")) {
            try {
                if (Long.parseLong(value.trim()) == pollId) return true;
            } catch (NumberFormatException ignored) {
                // Ignore invalid IDs; do not loosen the allowlist.
            }
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

}
