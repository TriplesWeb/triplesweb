package com.example.triplesweb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 예전 연결 계정 화면. 보유 카드는 닉네임 검색으로 보이므로 컬렉션 검색으로 보낸다.
 */
@Controller
public class CosmoAccountController {
    @GetMapping("/cosmo/account")
    public String account() {
        return "redirect:/cosmo/collection";
    }
}
