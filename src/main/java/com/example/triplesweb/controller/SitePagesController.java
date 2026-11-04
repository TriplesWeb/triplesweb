package com.example.triplesweb.controller;

import com.example.triplesweb.navigation.SectionNavView;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 앞으로 추가할 기능은 읽기 전용 안내 화면만 제공한다. 기존 게시판에 쓰기 경로를 연결하지 않는다. */
@Controller
public class SitePagesController {
    @GetMapping("/info")
    public String infoHub(Model model) {
        model.addAttribute("sectionNav", SectionNavView.of("info", "/info"));
        model.addAttribute("hubTitle", "정보");
        model.addAttribute("hubLead", "공연·굿즈·총공·기사 등 팬 유틸로 이동하세요.");
        return "hubs/section-hub";
    }

    @GetMapping("/activities/voting")
    public String voting(Model model) {
        model.addAttribute("sectionNav", SectionNavView.of("info", "/activities/voting"));
        return pending(model, "총공");
    }

    @GetMapping("/cosmo/group-orders")
    public String groupOrders() { return "redirect:/article/trade"; }

    private String pending(Model model, String title) {
        model.addAttribute("pageTitle", title);
        return "navigation/pending";
    }
}
