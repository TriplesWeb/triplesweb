package com.example.triplesweb.controller;

import com.example.triplesweb.service.CosmoCollectionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CosmoCollectionController {
    private final CosmoCollectionService collections;

    public CosmoCollectionController(CosmoCollectionService collections) {
        this.collections = collections;
    }

    @GetMapping("/cosmo/collection")
    public String collection(@RequestParam(required = false) String nickname,
                             @RequestParam(defaultValue = "0") int page,
                             Model model) {
        model.addAttribute("query", nickname == null ? "" : nickname.trim());
        if (nickname == null) return "cosmo/collection";
        if (nickname.isBlank()) {
            model.addAttribute("collectionError", "없는 닉네임 입니다.");
            return "cosmo/collection";
        }
        try {
            model.addAllAttributes(collections.search(nickname, page));
            model.addAttribute("hasCollection", true);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("collectionError", ex.getMessage());
        } catch (RuntimeException ex) {
            model.addAttribute("collectionError", "소유 오브젝트를 바로 불러오지 못했습니다. 잠시 후 다시 검색해 주세요.");
        }
        return "cosmo/collection";
    }
}
