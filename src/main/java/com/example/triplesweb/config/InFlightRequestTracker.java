package com.example.triplesweb.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Blue-Green drain: 이전 슬롯에 남은 진행 중 HTTP 요청 수를 센다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class InFlightRequestTracker extends OncePerRequestFilter {

    public static final String DRAIN_PATH = "/internal/deploy/inflight";

    private final AtomicInteger inFlight = new AtomicInteger();

    public int current() {
        return inFlight.get();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith(DRAIN_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        inFlight.incrementAndGet();
        try {
            filterChain.doFilter(request, response);
        } finally {
            inFlight.decrementAndGet();
        }
    }
}
