package com.ClinicaDeYmid.auth_service.infrastructure.web;

import com.ClinicaDeYmid.auth_service.application.StaffIdentity;
import com.ClinicaDeYmid.auth_service.application.login.LoginException;
import com.ClinicaDeYmid.auth_service.application.login.LoginFlow;
import com.ClinicaDeYmid.auth_service.application.login.LoginOutcome;
import com.ClinicaDeYmid.auth_service.infrastructure.security.AuthorizationServerProperties;
import com.ClinicaDeYmid.auth_service.infrastructure.security.StaffAuthentication;
import com.ClinicaDeYmid.auth_service.infrastructure.security.StaffPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
class LoginFlowController {

    static final String PENDING_PASSWORD_CHANGE = LoginFlowController.class.getName() + ".PENDING_PASSWORD_CHANGE";
    private static final Duration PENDING_PASSWORD_CHANGE_LIFETIME = Duration.ofMinutes(10);

    private final LoginFlow loginFlow;
    private final SecurityContextRepository contexts;
    private final RequestCache requestCache;
    private final AuthorizationServerProperties properties;
    private final Clock clock;

    LoginFlowController(LoginFlow loginFlow, SecurityContextRepository contexts, RequestCache requestCache,
                        AuthorizationServerProperties properties, Clock clock) {
        this.loginFlow = loginFlow;
        this.contexts = contexts;
        this.requestCache = requestCache;
        this.properties = properties;
        this.clock = clock;
    }

    record LoginRequest(String email, String password) {
    }

    record PasswordChangeRequest(String newPassword) {
    }

    record LoginResponse(String outcome, String continueUrl) {
    }

    record CsrfView(String headerName, String token) {
    }

    record UserView(UUID uuid, String email, String name, String role) {
    }

    record SessionView(boolean authenticated, UserView user, CsrfView csrf) {
    }

    record PendingPasswordChange(UUID userUuid, Instant expiresAt) implements Serializable {
    }

    @GetMapping("/session")
    SessionView session(HttpServletRequest request) {
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        CsrfView csrfView = new CsrfView(csrf.getHeaderName(), csrf.getToken());
        if (SecurityContextHolder.getContext().getAuthentication() instanceof StaffAuthentication staff) {
            StaffPrincipal principal = staff.getPrincipal();
            return new SessionView(true, new UserView(principal.uuid(), principal.email(), principal.fullName(), principal.role().name()), csrfView);
        }
        return new SessionView(false, null, csrfView);
    }

    @PostMapping("/login")
    LoginResponse login(@RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        return switch (loginFlow.login(body.email(), body.password(), request.getRemoteAddr())) {
            case LoginOutcome.Authenticated authenticated -> establish(authenticated.identity(), request, response);
            case LoginOutcome.PasswordChangeRequired required -> {
                request.getSession(true).setAttribute(PENDING_PASSWORD_CHANGE,
                        new PendingPasswordChange(required.userUuid(), Instant.now(clock).plus(PENDING_PASSWORD_CHANGE_LIFETIME)));
                yield new LoginResponse("PASSWORD_CHANGE_REQUIRED", null);
            }
        };
    }

    @PostMapping("/login/password-change")
    LoginResponse changeRequiredPassword(@RequestBody PasswordChangeRequest body, HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        Object pending = session == null ? null : session.getAttribute(PENDING_PASSWORD_CHANGE);
        if (!(pending instanceof PendingPasswordChange change) || Instant.now(clock).isAfter(change.expiresAt())) {
            throw new LoginException.PasswordChangeNotPending();
        }
        return establish(loginFlow.changeRequiredPassword(change.userUuid(), body.newPassword()), request, response);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    private LoginResponse establish(StaffIdentity identity, HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = requestCache.getRequest(request, response);
        HttpSession session = request.getSession(false);
        if (session == null) {
            session = request.getSession(true);
        } else {
            request.changeSessionId();
        }
        session.removeAttribute(PENDING_PASSWORD_CHANGE);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new StaffAuthentication(StaffPrincipal.withPassword(identity)));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        requestCache.removeRequest(request, response);
        return new LoginResponse("AUTHENTICATED", saved == null ? properties.homeUrl() : saved.getRedirectUrl());
    }
}
