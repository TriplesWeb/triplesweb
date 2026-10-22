package com.example.triplesweb.controller;

import com.example.triplesweb.service.GravityService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class GravityController {
    private final GravityService service;
    public GravityController(GravityService service) { this.service = service; }

    @GetMapping({"/gravity", "/cosmo/gravity"})
    public String gravity(Model model) {
        try {
            if (service.hasCosmoToken()) {
                var list = service.fetchGravityList("tripleS");
                model.addAttribute("gravityList", list);
                model.addAttribute("gravityListCount", list.size());
                model.addAttribute("emptyList", list.isEmpty());
            } else {
                model.addAttribute("gravityListError", "그래비티 정보를 준비 중입니다. 계정 연결이 완료되면 목록을 볼 수 있습니다.");
            }
        } catch (Exception e) {
            model.addAttribute("gravityListError", GravityService.describeApiError(e));
        }
        return "cosmo/gravity";
    }
}
