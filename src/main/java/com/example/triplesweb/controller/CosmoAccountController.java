package com.example.triplesweb.controller;

import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.CosmoAccountService;
import com.example.triplesweb.service.GravityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CosmoAccountController {
    private final AboutAdminAuth aboutAdminAuth;
    private final CosmoAccountService accountService;

    public CosmoAccountController(AboutAdminAuth aboutAdminAuth, CosmoAccountService accountService) {
        this.aboutAdminAuth = aboutAdminAuth;
        this.accountService = accountService;
    }

    @GetMapping("/cosmo/account")
    public String account(HttpServletRequest request, HttpSession session, Model model) {
        if (!aboutAdminAuth.isAdmin(request, session)) {
            model.addAttribute("needsAdmin", true);
            return "cosmo/account";
        }
        try {
            model.addAttribute("cosmoAccount", accountService.load());
            model.addAttribute("hasCosmoAccount", true);
        } catch (Exception e) {
            model.addAttribute("cosmoAccountError", GravityService.describeApiError(e));
        }
        return "cosmo/account";
    }
}
