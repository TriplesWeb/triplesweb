package com.example.triplesweb.controller;

import com.example.triplesweb.service.SiteSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class SearchController {

    private final SiteSearchService search;

    @GetMapping("/search")
    public String search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tab,
            Model model
    ) {
        String query = keyword == null ? "" : keyword.trim();
        model.addAttribute("keyword", query);
        if (query.isEmpty()) {
            model.addAttribute("errorMessage", "검색어를 입력해주세요.");
            SiteSearchService.Page empty = search.search("", tab);
            model.addAttribute("tabs", empty.getTabs());
            model.addAttribute("memberResults", empty.getMembers());
            model.addAttribute("hasMembers", false);
            model.addAttribute("hasAnyResults", false);
            model.addAttribute("activeTab", empty.getActiveTab());
            return "search";
        }
        SiteSearchService.Page page = search.search(query, tab);
        model.addAttribute("tabs", page.getTabs());
        model.addAttribute("memberResults", page.getMembers());
        model.addAttribute("hasMembers", page.isHasMembers());
        model.addAttribute("hasAnyResults", page.isHasAny());
        model.addAttribute("activeTab", page.getActiveTab());
        return "search";
    }
}
