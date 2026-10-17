package com.example.triplesweb.view;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Spring Security CSRF 토큰을 {@code _csrf} 모델 속성으로 Mustache 등 뷰에 전달한다.
 */
@ControllerAdvice
public class CsrfModelAdvice {
    @ModelAttribute("_csrf")
    public CsrfToken csrfToken(HttpServletRequest request) {
        return (org.springframework.security.web.csrf.CsrfToken)
                request.getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
    }
}
