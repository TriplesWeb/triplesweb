package com.example.triplesweb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 앞으로 추가할 기능은 읽기 전용 안내 화면만 제공한다. 기존 게시판에 쓰기 경로를 연결하지 않는다. */
@Controller
public class SitePagesController {
    @GetMapping("/sitemap")
    public String sitemap() { return "navigation/sitemap"; }

    @GetMapping("/activities/voting")
    public String voting(Model model) { return pending(model, "투표·노동"); }

    @GetMapping("/cosmo/group-orders")
    public String groupOrders(Model model) { return pending(model, "분철"); }

    @GetMapping("/media/drive")
    public String drive(Model model) { return pending(model, "트리플에스 드라이브"); }

    @GetMapping("/community/suggestions")
    public String suggestions(Model model) { return pending(model, "건의게시판 (비밀글)"); }

    @GetMapping("/sns")
    public String social(Model model) { return pending(model, "소셜 미디어"); }

    private String pending(Model model, String title) {
        model.addAttribute("pageTitle", title);
        return "navigation/pending";
    }
}
