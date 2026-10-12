package com.example.triplesweb.view;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CsrfModelAdvice {
    @ModelAttribute("_csrf")
    public CsrfToken csrfToken(HttpServletRequest request) {
        return (org.springframework.security.web.csrf.CsrfToken)
                request.getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
    }
}
