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
            Model model
    ) {
        String selectedArtist = normalizeArtist(artistId);
        model.addAttribute("artistId", selectedArtist);
        model.addAttribute("artistOptions", artistOptions(selectedArtist));
        model.addAttribute("cosmoConfigured", gravityService.hasCosmoToken());
        model.addAttribute("gravityCount", 0);
        model.addAttribute("hasGravityList", false);

        if (gravityService.hasCosmoToken()) {
            try {
                List<Map<String, Object>> liveGravities = gravityService.fetchGravityList(selectedArtist);
                model.addAttribute("gravityList", liveGravities);
                model.addAttribute("gravityCount", liveGravities.size());
                model.addAttribute("hasGravityList", !liveGravities.isEmpty());
            } catch (Exception exception) {
                model.addAttribute("gravityListError", GravityService.describeApiError(exception));
            }
        } else {
            model.addAttribute("cosmoNotice",
                    "COSMO_ACCESS_TOKEN이 설정되지 않아 실시간 그래비티 목록을 불러올 수 없습니다. .env 또는 실행 환경변수에 토큰을 설정해 주세요.");
        }

        // Vote results are shown on the selected Gravity detail page, not in this list.
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
