package com.example.triplesweb.controller;

import com.example.triplesweb.service.GravityService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class GravityController {
    private final GravityService service;
    public GravityController(GravityService service) { this.service = service; }

    @GetMapping({"/gravity", "/cosmo/gravity"})
    public String gravity(@RequestParam(name = "artistId", defaultValue = "tripleS") String artist,
            @RequestParam(name = "pollId", required = false) Long pollId, Model model) {
        String selected = "artms".equalsIgnoreCase(artist) ? "artms"
                : "idntt".equalsIgnoreCase(artist) ? "idntt" : "tripleS";
        model.addAttribute("artistId", selected);
        try {
            if (service.hasCosmoToken()) {
                var list = service.fetchGravityList(selected);
                model.addAttribute("gravityList", list);
                model.addAttribute("emptyList", list.isEmpty());
            } else {
                model.addAttribute("gravityListError", "그래비티 정보를 준비 중입니다. 계정 연결이 완료되면 목록을 볼 수 있습니다.");
            }
        } catch (Exception e) {
            model.addAttribute("gravityListError", GravityService.describeApiError(e));
        }
        if (pollId != null) {
            model.addAttribute("selectedPollId", pollId);
            try {
                model.addAttribute("aggregation", service.toMap(service.fetchAggregatedData(pollId)));
            } catch (Exception e) {
                model.addAttribute("aggregationError", GravityService.describeApiError(e));
            }
        }
        return "cosmo/gravity";
    }
}
