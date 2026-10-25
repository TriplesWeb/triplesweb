package com.example.triplesweb.controller;

import com.example.triplesweb.service.GravityService;
import com.example.triplesweb.util.GravityDisplay;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@Controller
public class GravityController {
    private final GravityService service;
    public GravityController(GravityService service) { this.service = service; }

    @GetMapping({"/gravity", "/cosmo/gravity"})
    public String gravity(Model model) {
        try {
            if (service.hasCosmoToken()) {
                List<Map<String, Object>> list = service.fetchGravityList("tripleS").stream()
                        .map(GravityDisplay::gravity).toList();
                model.addAttribute("gravityList", list);
                model.addAttribute("emptyList", list.isEmpty());
                model.addAttribute("hasGravityList", !list.isEmpty());
                model.addAttribute("gravityCount", list.size());
                model.addAttribute("gravityHighlights", list.stream().limit(4).toList());
                model.addAttribute("gravityOngoing", countStatus(list, "진행 중"));
                model.addAttribute("gravityUpcoming", countStatus(list, "예정"));
                long como = 0;
                boolean hasComo = false;
                for (Map<String, Object> gravity : list) {
                    if (Boolean.TRUE.equals(gravity.get("hasComo")) && gravity.get("comoUsed") instanceof Number number) {
                        como += number.longValue();
                        hasComo = true;
                    }
                }
                model.addAttribute("hasGravityComo", hasComo);
                model.addAttribute("gravityComoTotal", String.format("%,d", como));
            } else {
                model.addAttribute("gravityListError", "그래비티 정보를 준비 중입니다. 계정 연결이 완료되면 목록을 볼 수 있습니다.");
            }
        } catch (Exception e) {
            model.addAttribute("gravityListError", GravityService.describeApiError(e));
        }
        return "cosmo/gravity";
    }

    private static long countStatus(List<Map<String, Object>> list, String status) {
        return list.stream().filter(gravity -> status.equals(gravity.get("statusLabel"))).count();
    }
}
