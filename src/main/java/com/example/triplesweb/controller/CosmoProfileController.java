package com.example.triplesweb.controller;

import com.example.triplesweb.service.CosmoProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
@RequiredArgsConstructor
public class CosmoProfileController {
    private final CosmoProfileService profiles;

    @GetMapping("/cosmo/profile/{nickname}")
    public String profile(@PathVariable String nickname, @RequestParam(defaultValue = "") String tab,
                          @RequestParam(defaultValue = "") String member,
                          @RequestParam(defaultValue = "all") String group,
                          @RequestParam(defaultValue = "progress") String ownership,
                          @RequestParam(defaultValue = "0") int page, Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        if (tab.isBlank()) {
            String base = "/cosmo/profile/" + URLEncoder.encode(nickname, StandardCharsets.UTF_8).replace("+", "%20");
            return "redirect:" + base + (member.isBlank() && "all".equals(group) ? "?tab=overview" : "?tab=members&member=" + URLEncoder.encode(member, StandardCharsets.UTF_8) + "&group=" + URLEncoder.encode(group, StandardCharsets.UTF_8) + "&ownership=" + URLEncoder.encode(ownership, StandardCharsets.UTF_8));
        }
        if ("collection".equalsIgnoreCase(tab)) {
            return "redirect:/cosmo/collection?nickname="
                    + URLEncoder.encode(nickname, StandardCharsets.UTF_8).replace("+", "%20");
        }
        model.addAttribute("requestedNickname", nickname);
        model.addAttribute("transfersUrl", "/cosmo/transfers?nickname="
                + URLEncoder.encode(nickname, StandardCharsets.UTF_8));
        try {
            model.addAllAttributes(profiles.profile(nickname, member, group, tab, ownership));
            model.addAttribute("hasProfile", true);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("profileError", ex.getMessage());
        } catch (RuntimeException ex) {
            model.addAttribute("profileError", "닉네임 정보를 불러오지 못했습니다. 잠시 후 다시 확인해 주세요.");
        }
        return "cosmo/profile";
    }
}

