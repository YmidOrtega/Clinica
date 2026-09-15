package com.ClinicaDeYmid.auth_service.infrastructure.security;

import com.ClinicaDeYmid.commons.openbao.transit.OpenBaoUnavailableException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class OpenBaoOutageFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OpenBaoOutageFilter.class);
    private static final String RETRY_AFTER_SECONDS = "5";
    private static final byte[] BODY = """
            {"error": "temporarily_unavailable", "error_description": "The signing keys are unavailable; retry shortly"}"""
            .getBytes(StandardCharsets.UTF_8);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException failure) {
            if (!causedByOpenBao(failure) || response.isCommitted()) {
                throw failure;
            }
            log.warn("OpenBao is unavailable while serving {}; answering 503", request.getRequestURI());
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream().write(BODY);
        }
    }

    private static boolean causedByOpenBao(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof OpenBaoUnavailableException) {
                return true;
            }
        }
        return false;
    }
}
