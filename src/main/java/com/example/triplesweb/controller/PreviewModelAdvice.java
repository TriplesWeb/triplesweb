package com.example.triplesweb.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@Profile("preview")
@ControllerAdvice
public class PreviewModelAdvice {
    @Value("${app.csrf.cookie-name}")
    private String csrfCookieName;

    @ModelAttribute("isPreview")
    public boolean isPreview() { return true; }

    @ModelAttribute("previewCsrfCookieName")
    public String csrfCookieName() { return csrfCookieName; }
}
