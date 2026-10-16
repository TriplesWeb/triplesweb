package com.example.triplesweb.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.Model;
import com.example.triplesweb.dto.MemberForm;
import com.example.triplesweb.entity.Members;
import com.example.triplesweb.repository.MemberRepository;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Member;
import java.util.Optional;

/**
 * 로그인·로그아웃·회원가입 폼 및 세션 기반 인증.
 * URL: {@code /login}, {@code /logout}, {@code /signup}
 * 레이어: Should — MemberRepository·PasswordEncoder 직접 사용, MemberService 도입 검토.
 */
@Slf4j
@RequiredArgsConstructor
@Controller
public class LoginController {
    private final MemberRepository memberRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @GetMapping("/login")
    public String showLoginForm(@RequestParam(value = "error",required = false)String error,
                                @RequestParam(value = "message", required = false) String message,
                                Model model) {
        if (error != null) {
            if (message != null && !message.isBlank()) {
                model.addAttribute("errorMessage", message);
            } else {
                model.addAttribute("errorMessage", "아이디 또는 비밀번호가 일치하지 않습니다.");
            }
        }
        return "members/login";
    }
    @PostMapping("/login")
    public String login(@RequestParam("userId") String userId,
                        @RequestParam("password") String password,
                        HttpServletRequest request,
                        Model model) {
        Optional<Members> memberOpt =  memberRepository.findByUserId(userId);
        if (memberOpt.isEmpty()) {
            model.addAttribute("errorMessage", "존재하지 않는 아이디입니다.");
            return "members/login";
        }

        Members member = memberOpt.get();

        if (!passwordEncoder.matches(password, member.getPassword())) {
            model.addAttribute("errorMessage", "비밀번호가 일치하지 않습니다.");
            return "members/login";
        }
        HttpSession session = request.getSession();
        session.setAttribute("user", member);

        return "redirect:/";
    };


    @GetMapping("/logout")
    public String logout(HttpServletRequest request) {// 세션 초기화 (로그아웃)
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/";
    }

    @GetMapping("/signup")
    public String signupForm() {
        return "members/signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute MemberForm form, BindingResult bindingResult,@RequestParam(required = false) String emailLocal,
                         @RequestParam(required = false) String emailDomain, Model model) {
        if (emailLocal != null && !emailLocal.isBlank() &&
                emailDomain != null && !emailDomain.isBlank()) {
            form.setEmail(emailLocal + "@" + emailDomain);
        }
        if (form.getEmail() == null || form.getEmail().isBlank()) {
            bindingResult.rejectValue("email", "email.empty", "이메일은 필수입니다.");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("errorMessage", bindingResult.getFieldError().getDefaultMessage());
            return "members/signup";
        }
        Optional<Members> existing = memberRepository.findByUserId(form.getUserId());
        if (existing.isPresent()) {
            model.addAttribute("errorMessage", "이미 존재하는 아이디입니다.");
            return "members/signup";
        }
        Optional<Members> existingEmail = memberRepository.findByEmail(form.getEmail());
        if (existingEmail.isPresent()) {
            model.addAttribute("errorMessage", "이미 존재하는 이메일입니다.");
            return "members/signup";
        }
        String encodedPassword = passwordEncoder.encode(form.getPassword());

        Members newMember = new Members (
                form.getUserId(),
                encodedPassword,
                form.getEmail(),
                form.getAddress()
        );

        newMember.setRole("ROLE_USER");
        memberRepository.save(newMember);
        return "redirect:/";
    }
}
