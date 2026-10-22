package com.example.triplesweb.controller;

import com.example.triplesweb.service.GravityService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Read-only proxy for Apollo's public aggregation endpoint.
 * Only Poll IDs verified as belonging to tripleS are allowed; an unverified ID fails closed.
 */
@RestController
@RequestMapping("/api/cosmo/gravity")
public class GravityAggregateApiController {

    private final GravityService gravityService;

    public GravityAggregateApiController(GravityService gravityService) {
        this.gravityService = gravityService;
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
     * Apollo aggregation uses a Poll ID, not a Gravity ID.
     * Confirm the requested poll belongs to a Gravity in the tripleS COSMO list.
     */
    private Long resolveVerifiedTripleSPollId(long requestedId, Long gravityId) {
        try {
            Map<String, Object> mapping = gravityService.findTripleSPollMapping(requestedId);
            if (mapping == null) return null;
            if (gravityId != null
                    && ((Number) mapping.get("gravityId")).longValue() != gravityId) return null;
            return ((Number) mapping.get("pollId")).longValue();
        } catch (RuntimeException exception) {
            return null;
        }
    }

}
