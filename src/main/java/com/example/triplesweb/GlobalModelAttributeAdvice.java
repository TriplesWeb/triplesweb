package com.example.triplesweb;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * HTTP 세션의 {@code userid}를 뷰 모델 속성으로 노출한다.
 */
@ControllerAdvice
public class GlobalModelAttributeAdvice {

    @ModelAttribute("userid")
    public String addUseridToModel(HttpSession session) {
        return (String) session.getAttribute("userid");
    }
}
