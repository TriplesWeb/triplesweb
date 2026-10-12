package com.example.triplesweb;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAttributeAdvice {

    @ModelAttribute("userid")
    public String addUseridToModel(HttpSession session) {
        return (String) session.getAttribute("userid");
    }
}
