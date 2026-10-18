package com.example.triplesweb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/cosmo")
public class CosmoController {

    // 기존 헤더 링크를 유지하면서 실제 도감 페이지로 이동
    @GetMapping("/objekt")
    public String indexCosmoObjekt() {
        return "redirect:/objekt";
    }

    @GetMapping("/gravity")
    public String indexGravity() {
        return "cosmo/gravity";
    }

    @GetMapping("/event")
    public String indexCosmoEvent() {
        return "cosmo/event";
    }

    @GetMapping("/update")
    public String indexCosmoUpdate() {
        return "cosmo/update";
    }
}
