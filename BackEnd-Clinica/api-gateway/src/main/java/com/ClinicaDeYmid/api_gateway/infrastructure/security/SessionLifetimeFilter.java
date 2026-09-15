package com.ClinicaDeYmid.api_gateway.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

final class SessionLifetimeFilter extends OncePerRequestFilter {

    private final Duration absoluteLifetime;
    private final Clock clock;

    SessionLifetimeFilter(Duration absoluteLifetime, Clock clock) {
        this.absoluteLifetime = absoluteLifetime;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && Instant.ofEpochMilli(session.getCreationTime()).plus(absoluteLifetime).isBefore(Instant.now(clock))) {
            session.invalidate();
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }
}
