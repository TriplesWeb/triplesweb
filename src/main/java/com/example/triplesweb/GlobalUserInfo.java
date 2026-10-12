package com.example.triplesweb;

import com.example.triplesweb.entity.Members;
import com.example.triplesweb.repository.MemberRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalUserInfo {
    private final MemberRepository memberRepository;

    public GlobalUserInfo(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @ModelAttribute("user")
    public Members addUserToModel() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && !(authentication.getPrincipal() instanceof String)) {
            User user = (User) authentication.getPrincipal();
            return memberRepository.findByUserId(user.getUsername()).orElse(null);
        }

        return null;
    }

    @ModelAttribute("member")
    public Members addMemberToModel() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && !(authentication.getPrincipal() instanceof String)) {
            User user = (User) authentication.getPrincipal();
            // username(아이디)로 DB에서 Members 엔티티 조회
            return memberRepository.findByUserId(user.getUsername())
                    .orElse(null);
        }
        return null;
    }
    @ModelAttribute("isAdmin")
    public boolean addIsAdmin(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}

