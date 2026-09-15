package com.ClinicaDeYmid.api_gateway.infrastructure.security;

import com.ClinicaDeYmid.api_gateway.domain.ratelimit.RateLimitDecision;
import com.ClinicaDeYmid.api_gateway.domain.ratelimit.RateLimitPolicy;
import com.ClinicaDeYmid.api_gateway.domain.ratelimit.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

final class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;
    private final RateLimitPolicy policy;
    private final Function<HttpServletRequest, Optional<String>> subject;
    private final ObjectMapper json;

    RateLimitFilter(RateLimiter limiter, RateLimitPolicy policy, Function<HttpServletRequest, Optional<String>> subject, ObjectMapper json) {
        this.limiter = limiter;
        this.policy = policy;
        this.subject = subject;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/") || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        Optional<String> key = subject.apply(request);
        if (key.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        switch (limiter.acquire(policy, key.get())) {
            case RateLimitDecision.Allowed allowed -> {
                response.setHeader("RateLimit-Limit", Integer.toString(allowed.limit()));
                response.setHeader("RateLimit-Remaining", Long.toString(allowed.remaining()));
                response.setHeader("RateLimit-Reset", Long.toString(seconds(allowed.reset())));
                chain.doFilter(request, response);
            }
            case RateLimitDecision.Limited limited -> {
                response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds(limited.retryAfter())));
                response.setHeader("RateLimit-Limit", Integer.toString(limited.limit()));
                response.setHeader("RateLimit-Remaining", "0");
                ProblemResponses.write(response, json, HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                        "Demasiadas solicitudes; espera antes de volver a intentar", Map.of());
            }
            case RateLimitDecision.Unknown unknown -> chain.doFilter(request, response);
        }
    }

    private static long seconds(Duration duration) {
        return Math.max(1, (duration.toMillis() + 999) / 1000);
    }
}
