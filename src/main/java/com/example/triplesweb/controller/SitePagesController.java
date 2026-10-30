package com.example.triplesweb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 앞으로 추가할 기능은 읽기 전용 안내 화면만 제공한다. 기존 게시판에 쓰기 경로를 연결하지 않는다. */
@Controller
public class SitePagesController {
    @GetMapping("/activities/voting")
    public String voting(Model model) { return pending(model, "총공"); }

    @GetMapping("/cosmo/group-orders")
    public String groupOrders() { return "redirect:/article/group-orders"; }

    private String pending(Model model, String title) {
        model.addAttribute("pageTitle", title);
        return "navigation/pending";
    }
}
