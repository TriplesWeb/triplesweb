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
@RequiredArgsConstructor
public class ObjektController {
    private final ObjektService objektService;

    @GetMapping("/objekt")
    public String objektPage(Model model) {
        List<Map<String, Object>> objekts;
        objekts = objektService.findCollections("triples");

        List<String> objektNumbers = objekts.stream()
                .map(objekt -> String.valueOf(objekt.get("collectionNo")))
                .filter(number -> !number.isBlank())
                .distinct()
                .sorted()
                .toList();
        String error = "";
        try {
            objekts = objektService.findCollections("triples");
            if (objekts.isEmpty()) error = "API 응답에 표시할 컬렉션이 없습니다.";
        } catch (RestClientException ex) {
            objekts = Collections.emptyList();
            error = "Objekt API에 연결하지 못했습니다. 잠시 후 새로고침해 주세요.";
        }
        model.addAttribute("objekts", objekts);
        model.addAttribute("objektCount", objekts.size());
        model.addAttribute("objektError", error);
        model.addAttribute("objektNumbers", objektNumbers);
        return "objekt";
    }
}
