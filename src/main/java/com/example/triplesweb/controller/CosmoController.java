package com.example.triplesweb.controller;

import com.example.triplesweb.service.ObjektService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/cosmo")
@RequiredArgsConstructor
public class CosmoController {

    private final ObjektService objektService;

    // 기존 헤더 링크를 유지하면서 실제 도감 페이지로 이동
    @GetMapping("/objekt")
    public String indexCosmoObjekt(Model model) {

            List<Map<String, Object>> objekts;
            String error = "";

            try {
                objekts = objektService.findCollections("triples");

                if (objekts == null) {
                    objekts = Collections.emptyList();
                }

                if (objekts.isEmpty()) {
                    error = "API 응답에 표시할 컬렉션이 없습니다.";
                }

            } catch (RestClientException ex) {
                objekts = Collections.emptyList();
                error = "Objekt API에 연결하지 못했습니다. 잠시 후 새로고침해 주세요.";

                // 실제 원인을 서버 로그에서 확인할 수 있도록 기록
                ex.printStackTrace();
            }

            List<String> objektNumbers = objekts.stream()
                    .map(objekt -> String.valueOf(objekt.getOrDefault("collectionNo", "")))
                    .filter(number -> !number.isBlank() && !"null".equals(number))
                    .distinct()
                    .sorted()
                    .toList();

            model.addAttribute("objekts", objekts);
            model.addAttribute("objektCount", objekts.size());
            model.addAttribute("objektError", error);
            model.addAttribute("objektNumbers", objektNumbers);

            return "cosmo/objekt";
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
