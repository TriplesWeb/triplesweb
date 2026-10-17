package com.example.triplesweb.view;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 세션의 {@code userid}와 로그인 여부를 모든 뷰 모델에 추가한다.
 */
@ControllerAdvice
public class GlobalModelAdvice {
    @ModelAttribute("userid")
    public String userid(HttpSession session) {
        String uid = (String) session.getAttribute("userid");
        return uid == null ? "" : uid; // null 방지
    }

    @ModelAttribute("isLoggedIn")
    public boolean isLoggedIn(HttpSession session) {
        return session.getAttribute("userid") != null;
    }
}
