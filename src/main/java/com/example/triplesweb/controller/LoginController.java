package com.example.triplesweb.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 로그인·로그아웃·회원가입 — 현재 비활성화 (메인으로 리다이렉트).
 * URL: {@code /login}, {@code /logout}, {@code /signup}
 */
@Controller
public class LoginController {

    /** 로그인 기능 비활성화 — 메인으로 리다이렉트 */
    @GetMapping("/login")
    public String showLoginForm() {
        return "redirect:/";
    }

    @PostMapping("/login")
    public String login() {
        return "redirect:/";
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/";
    }

    @GetMapping("/signup")
    public String signupForm() {
        return "redirect:/";
    }

    @PostMapping("/signup")
    public String signup() {
        return "redirect:/";
    }
}
