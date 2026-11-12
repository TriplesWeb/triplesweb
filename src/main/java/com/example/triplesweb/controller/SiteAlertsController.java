package com.example.triplesweb.controller;

import com.example.triplesweb.service.SiteAlertsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SiteAlertsController {

    private final SiteAlertsService alerts;

    @GetMapping("/api/site/alerts")
    public Map<String, Object> alerts() {
        return alerts.alerts();
    }
}
