package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Members;
import com.example.triplesweb.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.core.userdetails.User;

import java.util.Optional;

@Controller
public class MyPageController {

    @Autowired
    private MemberRepository memberRepository;

    // 내 정보 보기
    @GetMapping("/mypage")
    public String viewMyPage(@AuthenticationPrincipal User user, Model model) {
        if (user == null) return "redirect:/a";

        Optional<Members> optionalMember = memberRepository.findByUserId(user.getUsername());
        if (optionalMember.isEmpty()) return "redirect:/login";

        Members member = optionalMember.get();
        model.addAttribute("member", member);
        return "members/mypage";  // mypage.mustache 또는 mypage.html
    }

    // 내 정보 수정 폼
    @GetMapping("/mypage_edit")
    public String editMyPageForm(@AuthenticationPrincipal User user, Model model) {
        if (user == null) return "redirect:/login";

        Optional<Members> optionalMember = memberRepository.findByUserId(user.getUsername());
        if (optionalMember.isEmpty()) return "redirect:/login";

        model.addAttribute("member", optionalMember.get());
        return "members/mypage_edit";
    }

    // 내 정보 수정 처리
    @PostMapping("/mypage_edit")
    public String editMyPageSubmit(@AuthenticationPrincipal User user,
                                   @ModelAttribute Members updatedMember) {
        if (user == null) return "redirect:/members/login";

        Optional<Members> optionalMember = memberRepository.findByUserId(user.getUsername());
        if (optionalMember.isPresent()) {
            Members member = optionalMember.get();
            member.setEmail(updatedMember.getEmail());
            member.setAddress(updatedMember.getAddress());

            // 비밀번호가 비어있지 않으면 업데이트
            if (updatedMember.getPassword() != null && !updatedMember.getPassword().isEmpty()) {
                BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
                member.setPassword(encoder.encode(updatedMember.getPassword()));
            }

            memberRepository.save(member);
        }
        return "redirect:/members/mypage";
    }
}

