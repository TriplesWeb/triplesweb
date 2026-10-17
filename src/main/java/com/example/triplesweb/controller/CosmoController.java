package com.example.triplesweb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 헤더의 코스모탭을 관리,업뎃,수정,삭제한다.
 * URL: {@code /archive/**} — {@code /archive/{type}_archive}, {@code /archive/upload}, …
 * 레이어: OK — ArchiveService 사용.
 */
@Controller
@RequestMapping("/cosmo")
public class CosmoController {

    @GetMapping("/objekt")
    public String indexCosmoObjekt(Model model) {
        return "/cosmo/objekt";
    }
    @GetMapping("/gravity")
    public String indexGravity(Model model) {
        return "/cosmo/gravity";
    }
    @GetMapping("/event")
    public String indexCosmo(Model model) {
        return "/cosmo/event";
    }
    @GetMapping("/update")
    public String indexCosmoUpdate(Model model) {
        return "/cosmo/update";
    }
}
