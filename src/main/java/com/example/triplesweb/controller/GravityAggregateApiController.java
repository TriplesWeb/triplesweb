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
 * Read-only proxy for Apollo's public Gravity aggregation endpoint.
 * This endpoint does not require a COSMO access token; it only needs a COSMO Poll ID.
 */
@RestController
@RequestMapping("/api/cosmo/gravity")
public class GravityAggregateApiController {

    private final GravityService gravityService;

    public GravityAggregateApiController(GravityService gravityService) {
        this.gravityService = gravityService;
    }

    @GetMapping("/aggregate")
    public ResponseEntity<?> aggregate(@RequestParam long pollId) {
        if (pollId <= 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Poll ID는 1 이상의 숫자여야 합니다."));
        }

        try {
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
}
