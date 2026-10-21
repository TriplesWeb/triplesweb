package com.example.triplesweb.controller;

import com.example.triplesweb.service.GravityService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class GravityController {
    private static final List<Map<String, Object>> ARTISTS = List.of(
            artistOption("tripleS", "tripleS"),
            artistOption("artms", "ARTMS"),
            artistOption("idntt", "idntt")
    );

    private final GravityService gravityService;

    public GravityController(GravityService gravityService) {
        this.gravityService = gravityService;
    }

    @GetMapping("/gravity")
    public String gravity(
            @RequestParam(defaultValue = "tripleS") String artistId,
            @RequestParam(required = false) Long pollId,
            Model model
    ) {
        String selectedArtist = normalizeArtist(artistId);
        model.addAttribute("artistId", selectedArtist);
        model.addAttribute("artistOptions", artistOptions(selectedArtist));
        model.addAttribute("cosmoConfigured", gravityService.hasCosmoToken());
        model.addAttribute("selectedPollId", pollId);

        if (gravityService.hasCosmoToken()) {
            try {
                model.addAttribute("gravityList", gravityService.fetchGravityList(selectedArtist));
            } catch (Exception exception) {
                model.addAttribute("gravityListError", GravityService.describeApiError(exception));
            }
        } else {
            model.addAttribute("cosmoNotice",
                    "COSMO 액세스 토큰이 설정되지 않았습니다. 투표 ID 집계 조회를 먼저 시도할 수 있으며, " +
                    "Gravity 목록과 후보명을 보려면 본인 계정의 유효한 토큰을 서버 환경 변수로 설정해야 합니다.");
        }

        if (pollId != null) {
            if (pollId <= 0) {
                model.addAttribute("aggregationError", "투표 ID는 1 이상의 숫자여야 합니다.");
                return "gravity";
            }

            JsonNode aggregateNode = null;
            try {
                aggregateNode = gravityService.fetchAggregatedData(pollId);
                model.addAttribute("aggregation", gravityService.toMap(aggregateNode));
            } catch (Exception exception) {
                model.addAttribute("aggregationError", GravityService.describeApiError(exception));
            }

            if (gravityService.hasCosmoToken() && aggregateNode != null) {
                try {
                    JsonNode pollDetails = gravityService.fetchPollDetails(pollId);
                    model.addAttribute("candidateResults",
                            gravityService.buildCandidateResults(pollDetails, aggregateNode));
                    if (pollDetails != null) {
                        model.addAttribute("pollDetails", gravityService.toMap(pollDetails));
                    } else {
                        model.addAttribute("candidateNotice",
                                "COSMO API에서 후보 정보를 반환하지 않았습니다.");
                    }
                } catch (Exception exception) {
                    model.addAttribute("candidateNotice",
                            "집계 데이터는 조회했지만 후보 정보를 가져오지 못했습니다. " +
                            GravityService.describeApiError(exception));
                }
            } else if (aggregateNode != null) {
                model.addAttribute("candidateNotice",
                        "후보명은 COSMO 토큰이 설정되면 표시됩니다. 현재는 집계 API가 반환한 후보 ID만 확인할 수 있습니다.");
            }
        }

        return "gravity";
    }

    private static String normalizeArtist(String artistId) {
        if ("artms".equalsIgnoreCase(artistId)) return "artms";
        if ("idntt".equalsIgnoreCase(artistId)) return "idntt";
        return "tripleS";
    }

    private static Map<String, Object> artistOption(String value, String label) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("value", value);
        option.put("label", label);
        return option;
    }

    private static List<Map<String, Object>> artistOptions(String selected) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (Map<String, Object> original : ARTISTS) {
            Map<String, Object> option = new LinkedHashMap<>(original);
            option.put("selected", selected.equals(option.get("value")));
            options.add(option);
        }
        return options;
    }
}
