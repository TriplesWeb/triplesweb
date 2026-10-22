package com.example.triplesweb.controller;

import com.example.triplesweb.service.GravityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/** Read-only Gravity proxy. Account credentials never leave the server. */
@RestController
@RequestMapping("/api/gravity")
public class GravityApiController {
    private final GravityService service;
    public GravityApiController(GravityService service) { this.service = service; }

    @GetMapping
    public Object list(@RequestParam(name = "artistId", defaultValue = "tripleS") String artist) {
        if (!java.util.Set.of("tripleS", "artms", "idntt").contains(artist)) {
            throw new IllegalArgumentException("Invalid artist");
        }
        return service.fetchGravityList(artist);
    }

    @GetMapping("/{id:[0-9]+}")
    public Object detail(@PathVariable("id") long id) {
        return service.toMap(service.fetchGravityDetails(id));
    }

    @GetMapping("/polls/{id:[0-9]+}")
    public Object poll(@PathVariable("id") long id) {
        return service.toMap(service.fetchPollDetails(id));
    }

    @GetMapping("/polls/{id:[0-9]+}/aggregated")
    public Object aggregate(@PathVariable("id") long id) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of("message",
                "시간대별 투표 집계는 현재 제공되지 않습니다. 공식 결과는 그래비티 상세에서 확인해 주세요."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> error(Exception error) {
        HttpStatus status = error instanceof IllegalArgumentException ? HttpStatus.BAD_REQUEST
                : error instanceof IllegalStateException ? HttpStatus.SERVICE_UNAVAILABLE
                : error instanceof RestClientResponseException response && response.getStatusCode().value() == 404
                ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(Map.of("message", GravityService.describeApiError(error)));
    }
}
