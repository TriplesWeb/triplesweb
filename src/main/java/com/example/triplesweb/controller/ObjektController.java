package com.example.triplesweb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Objekt 페이지 스텁 — 정적 뷰만 반환한다.
 * URL: {@code GET /objekt}
 * 레이어: stub — Service·도메인 로직 없음, 추후 확장 시 분리.
 */
@Controller
public class ObjektController {

    @GetMapping("/objekt")
    public String ObjektPage() {
        return "objekt";
    }
}
