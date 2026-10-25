package com.example.triplesweb.controller;

import com.example.triplesweb.service.CosmoProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class CosmoProfileController {
    private final CosmoProfileService profiles;

    @GetMapping("/cosmo/profile/{nickname}")
    public String profile(@PathVariable String nickname, @RequestParam(defaultValue = "progress") String tab,
                          @RequestParam(defaultValue = "") String member,
                          @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("requestedNickname", nickname);
        try {
            model.addAllAttributes(profiles.profile(nickname, tab, member, page));
            model.addAttribute("hasProfile", true);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("profileError", ex.getMessage());
        } catch (RuntimeException ex) {
            model.addAttribute("profileError", "닉네임 정보를 불러오지 못했습니다. 잠시 후 다시 확인해 주세요.");
        }
        return "cosmo/profile";
    }
}
