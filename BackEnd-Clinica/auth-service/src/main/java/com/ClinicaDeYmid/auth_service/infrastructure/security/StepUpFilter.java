package com.ClinicaDeYmid.auth_service.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public final class StepUpFilter extends OncePerRequestFilter {

    public static final String STEP_UP_REQUIRED = StepUpFilter.class.getName() + ".STEP_UP_REQUIRED";

    private static final String AUTHORIZATION_ENDPOINT = "/oauth2/authorize";
    private static final String MAX_AGE = "max_age";

    private final RequestCache requestCache;
    private final String loginUrl;
    private final Clock clock;

    StepUpFilter(RequestCache requestCache, String loginUrl, Clock clock) {
        this.requestCache = requestCache;
        this.loginUrl = loginUrl;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (AUTHORIZATION_ENDPOINT.equals(request.getServletPath())
                && SecurityContextHolder.getContext().getAuthentication() instanceof StaffAuthentication staff
                && tooOld(staff.getPrincipal(), request.getParameter(MAX_AGE))) {
            requestCache.saveRequest(request, response);
            request.getSession().setAttribute(STEP_UP_REQUIRED, Boolean.TRUE);
            response.sendRedirect(UriComponentsBuilder.fromUriString(loginUrl).queryParam("step", "step-up").build().toUriString());
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean tooOld(StaffPrincipal principal, String maxAge) {
        if (maxAge == null || !maxAge.matches("\\d{1,9}")) {
            return false;
        }
        Duration elapsed = Duration.between(principal.authenticatedAt(), Instant.now(clock));
        return elapsed.getSeconds() > Long.parseLong(maxAge);
    }
}
