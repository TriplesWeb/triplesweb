package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GravityServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GravityService newService() {
        return new GravityService(
                RestClient.builder(),
                objectMapper,
                "https://apollo.cafe",
                "https://api.cosmo.fans",
                ""
        );
    }

    @Test
    void detailMappingDoesNotInventComoTotalsBeforeResultsArePublished() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "id": 123,
                  "title": "Test Gravity",
                  "type": "GravityEvent",
                  "description": "Example description",
                  "startDate": "2026-10-20T10:00:00+09:00",
                  "endDate": "2026-10-21T10:00:00+09:00",
                  "polls": [
                    {
                      "id": 456,
                      "choices": [
                        { "title": "Candidate A" },
                        { "title": "Candidate B" }
                      ]
                    }
                  ]
                }
                """);

        Map<String, Object> detail = newService().buildGravityDetailView(payload);

        assertEquals("123", detail.get("id"));
        assertEquals("Test Gravity", detail.get("title"));
        assertEquals("2026.10.20 ~ 2026.10.21", detail.get("period"));
        assertEquals("결과 미공개", detail.get("como"));
        assertEquals(2, detail.get("candidateCount"));
        assertTrue((Boolean) detail.get("hasCandidates"));
        assertFalse((Boolean) detail.get("hasLeaderboard"));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> candidates = (List<Map<String, String>>) detail.get("candidates");
        assertEquals("Candidate A", candidates.get(0).get("name"));
        assertEquals("투표 후보", candidates.get(0).get("type"));
        assertEquals("", candidates.get(0).get("amount"));
    }

    @Test
    void detailMappingShowsPublishedComoAmountsAndRankings() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "id": 124,
                  "title": "Finished Gravity",
                  "result": { "totalComoUsed": 1000, "resultTitle": "Winner A" },
                  "polls": [
                    {
                      "id": 457,
                      "choices": [{ "title": "Candidate A" }],
                      "result": {
                        "totalComoUsed": 1000,
                        "results": [
                          { "rank": 1, "title": "Candidate A", "comoUsed": 1000 }
                        ]
                      }
                    }
                  ],
                  "leaderboard": [
                    { "rank": 1, "nickname": "fan", "comoUsed": 1000 }
                  ]
                }
                """);

        Map<String, Object> detail = newService().buildGravityDetailView(payload);

        assertEquals("1,000 COMO", detail.get("como"));
        assertTrue((Boolean) detail.get("hasLeaderboard"));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> candidates = (List<Map<String, String>>) detail.get("candidates");
        assertEquals("1위", candidates.get(0).get("type"));
        assertEquals("1,000 COMO", candidates.get(0).get("amount"));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> leaderboard = (List<Map<String, String>>) detail.get("leaderboard");
        assertEquals("fan", leaderboard.get(0).get("nickname"));
        assertEquals("1,000 COMO", leaderboard.get(0).get("como"));
    }
}
