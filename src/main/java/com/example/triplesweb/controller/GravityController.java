package com.example.triplesweb.controller;

import com.example.triplesweb.service.GravityService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class GravityController {
    // This community page intentionally exposes tripleS Gravity only.
    private static final List<Map<String, Object>> ARTISTS = List.of(
            artistOption("tripleS", "tripleS")
    );

    private final GravityService gravityService;

    public GravityController(GravityService gravityService) {
        this.gravityService = gravityService;
    }

    @GetMapping({"/gravity", "/cosmo/gravity"})
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
        model.addAttribute("gravityCount", 0);

        if (gravityService.hasCosmoToken()) {
            try {
                List<Map<String, Object>> liveGravities = gravityService.fetchGravityList(selectedArtist);
                model.addAttribute("gravityList", liveGravities);
                model.addAttribute("gravityCount", liveGravities.size());
            } catch (Exception exception) {
                model.addAttribute("gravityListError", GravityService.describeApiError(exception));
            }
        } else {
            model.addAttribute("cosmoNotice",
                    "COSMO 액세스 토큰이 설정되지 않았습니다. 투표 ID 집계 조회를 먼저 시도할 수 있으며, " +
                    "Gravity 목록과 후보명을 보려면 본인 계정의 유효한 토큰을 서버 환경 변수로 설정해야 합니다.");
        }

        if (pollId != null && pollId <= 0) {
            model.addAttribute("aggregationError", "투표 ID는 1 이상의 숫자여야 합니다.");
        }

        // The browser queries the public Apollo aggregate endpoint through
        // GravityAggregateApiController and refreshes it every 30 seconds.
        return "cosmo/gravity";
    }

    private static String normalizeArtist(String artistId) {
        // Ignore arbitrary query parameters so this page can never switch to another artist.
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
