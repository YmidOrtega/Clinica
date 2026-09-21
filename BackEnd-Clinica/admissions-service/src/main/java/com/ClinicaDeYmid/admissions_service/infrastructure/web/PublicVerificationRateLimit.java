package com.ClinicaDeYmid.admissions_service.infrastructure.web;

import com.ClinicaDeYmid.commons.error.ErrorCategory;
import com.ClinicaDeYmid.commons.web.ProblemDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
class PublicVerificationRateLimit extends OncePerRequestFilter {

    static final String PATH = "/api/v1/admissions/receipts/verification";

    private static final Logger log = LoggerFactory.getLogger(PublicVerificationRateLimit.class);

    private final ObjectMapper json;
    private final Cache<String, AtomicInteger> attempts;
    private final int limit;

    PublicVerificationRateLimit(ObjectMapper json,
                                @Value("${clinica.admissions.receipts.public-checks-per-window:20}") int limit,
                                @Value("${clinica.admissions.receipts.public-window:1m}") Duration window) {
        this.json = json;
        this.limit = limit;
        this.attempts = Caffeine.newBuilder().expireAfterWrite(window).maximumSize(100_000).build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String caller = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        int used = attempts.get(caller, key -> new AtomicInteger()).incrementAndGet();
        if (used > limit) {
            log.warn("Public receipt verification refused: {} went over {} checks in the window", caller, limit);
            ProblemDetail problem = ProblemDetails.of(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_CHECKS",
                    "Demasiadas comprobaciones seguidas; espera un momento antes de volver a intentarlo");
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            json.writeValue(response.getOutputStream(), problem);
            return;
        }
        chain.doFilter(request, response);
    }
}
