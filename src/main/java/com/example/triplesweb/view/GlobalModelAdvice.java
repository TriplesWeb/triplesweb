package com.example.triplesweb.view;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

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
