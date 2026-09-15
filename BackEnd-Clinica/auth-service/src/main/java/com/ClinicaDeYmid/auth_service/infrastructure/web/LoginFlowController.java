package com.ClinicaDeYmid.auth_service.infrastructure.web;

import com.ClinicaDeYmid.auth_service.application.StaffIdentity;
import com.ClinicaDeYmid.auth_service.application.login.LoginException;
import com.ClinicaDeYmid.auth_service.application.login.LoginFlow;
import com.ClinicaDeYmid.auth_service.application.login.LoginOutcome;
import com.ClinicaDeYmid.auth_service.application.login.SecondFactorFlow;
import com.ClinicaDeYmid.auth_service.domain.secondfactor.SecondFactorProof;
import com.ClinicaDeYmid.auth_service.domain.secondfactor.TotpAuthenticator;
import com.ClinicaDeYmid.auth_service.infrastructure.security.AuthorizationServerProperties;
import com.ClinicaDeYmid.auth_service.infrastructure.security.StaffAuthentication;
import com.ClinicaDeYmid.auth_service.infrastructure.security.StaffPrincipal;
import com.ClinicaDeYmid.auth_service.infrastructure.security.StepUpFilter;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/v1")
class LoginFlowController {

    static final String PENDING_SIGN_IN = LoginFlowController.class.getName() + ".PENDING_SIGN_IN";
    private static final Duration PENDING_SIGN_IN_LIFETIME = Duration.ofMinutes(10);

    enum PendingStep {
        PASSWORD_CHANGE,
        SECOND_FACTOR,
        SECOND_FACTOR_ENROLLMENT,
        STEP_UP
    }

    record PendingSignIn(UUID userUuid, PendingStep step, Instant expiresAt) implements Serializable {
    }

    record LoginRequest(String email, String password) {
    }

    record PasswordChangeRequest(String newPassword) {
    }

    record SecondFactorRequest(String code, String recoveryCode) {

        SecondFactorProof proof() {
            return recoveryCode != null && !recoveryCode.isBlank()
                    ? new SecondFactorProof.RecoveryCode(recoveryCode)
                    : new SecondFactorProof.Totp(code);
        }
    }

    record StepResponse(String outcome, String continueUrl, List<String> recoveryCodes, Integer remainingRecoveryCodes) {

        static StepResponse pending(PendingStep step) {
            return new StepResponse(step.name() + "_REQUIRED", null, null, null);
        }
    }

    record EnrollmentView(String otpauthUrl, String qrPngBase64) {
    }

    record CsrfView(String headerName, String token) {
    }

    record UserView(UUID uuid, String email, String name, String role, Instant authenticatedAt) {
    }

    record SessionView(boolean authenticated, UserView user, String pendingStep, CsrfView csrf) {
    }

    private final LoginFlow loginFlow;
    private final SecondFactorFlow secondFactorFlow;
    private final SecurityContextRepository contexts;
    private final RequestCache requestCache;
    private final AuthorizationServerProperties properties;
    private final Clock clock;

    LoginFlowController(LoginFlow loginFlow, SecondFactorFlow secondFactorFlow, SecurityContextRepository contexts, RequestCache requestCache,
                        AuthorizationServerProperties properties, Clock clock) {
        this.loginFlow = loginFlow;
        this.secondFactorFlow = secondFactorFlow;
        this.contexts = contexts;
        this.requestCache = requestCache;
        this.properties = properties;
        this.clock = clock;
    }

    @GetMapping("/session")
    SessionView session(HttpServletRequest request) {
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        CsrfView csrfView = new CsrfView(csrf.getHeaderName(), csrf.getToken());
        HttpSession session = request.getSession(false);
        if (SecurityContextHolder.getContext().getAuthentication() instanceof StaffAuthentication staff) {
            StaffPrincipal principal = staff.getPrincipal();
            boolean stepUp = session != null && session.getAttribute(StepUpFilter.STEP_UP_REQUIRED) != null;
            return new SessionView(true, new UserView(principal.uuid(), principal.email(), principal.fullName(), principal.role().name(),
                    principal.authenticatedAt()), stepUp ? PendingStep.STEP_UP.name() : null, csrfView);
        }
        String pending = pending(request).map(sign -> sign.step().name()).orElse(null);
        return new SessionView(false, null, pending, csrfView);
    }

    @PostMapping("/login")
    StepResponse login(@RequestBody LoginRequest body, HttpServletRequest request) {
        return advance(loginFlow.login(body.email(), body.password(), request.getRemoteAddr()), request);
    }

    @PostMapping("/login/password-change")
    StepResponse changeRequiredPassword(@RequestBody PasswordChangeRequest body, HttpServletRequest request) {
        PendingSignIn pending = require(request, PendingStep.PASSWORD_CHANGE, LoginException.PasswordChangeNotPending::new);
        return advance(loginFlow.changeRequiredPassword(pending.userUuid(), body.newPassword()), request);
    }

    @PostMapping("/login/second-factor/enrollment")
    EnrollmentView startEnrollment(HttpServletRequest request) {
        PendingSignIn pending = require(request, PendingStep.SECOND_FACTOR_ENROLLMENT, LoginException.SecondFactorNotPending::new);
        TotpAuthenticator.TotpEnrollment enrollment = secondFactorFlow.startEnrollment(pending.userUuid());
        return new EnrollmentView(enrollment.otpauthUrl(), enrollment.qrPngBase64());
    }

    @PostMapping("/login/second-factor/enrollment/confirmation")
    StepResponse confirmEnrollment(@RequestBody SecondFactorRequest body, HttpServletRequest request, HttpServletResponse response) {
        PendingSignIn pending = require(request, PendingStep.SECOND_FACTOR_ENROLLMENT, LoginException.SecondFactorNotPending::new);
        SecondFactorFlow.EnrollmentCompleted completed = secondFactorFlow.confirmEnrollment(pending.userUuid(), body.code());
        String continueUrl = establish(completed.identity(), request, response);
        return new StepResponse("AUTHENTICATED", continueUrl, completed.recoveryCodes(), completed.recoveryCodes().size());
    }

    @PostMapping("/login/second-factor")
    StepResponse verifySecondFactor(@RequestBody SecondFactorRequest body, HttpServletRequest request, HttpServletResponse response) {
        PendingSignIn pending = require(request, PendingStep.SECOND_FACTOR, LoginException.SecondFactorNotPending::new);
        SecondFactorFlow.Verified verified = secondFactorFlow.verify(pending.userUuid(), body.proof(), SecondFactorFlow.Purpose.SIGN_IN);
        return new StepResponse("AUTHENTICATED", establish(verified.identity(), request, response), null, verified.remainingRecoveryCodes());
    }

    @PostMapping("/login/step-up")
    StepResponse stepUp(@RequestBody SecondFactorRequest body, HttpServletRequest request, HttpServletResponse response) {
        StaffAuthentication current = (StaffAuthentication) SecurityContextHolder.getContext().getAuthentication();
        SecondFactorFlow.Verified verified = secondFactorFlow.verify(current.getPrincipal().uuid(), body.proof(), SecondFactorFlow.Purpose.STEP_UP);
        return new StepResponse("AUTHENTICATED", establish(verified.identity(), request, response), null, verified.remainingRecoveryCodes());
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

    private StepResponse advance(LoginOutcome outcome, HttpServletRequest request) {
        PendingStep step = switch (outcome) {
            case LoginOutcome.PasswordChangeRequired required -> PendingStep.PASSWORD_CHANGE;
            case LoginOutcome.SecondFactorRequired required -> PendingStep.SECOND_FACTOR;
            case LoginOutcome.SecondFactorEnrollmentRequired required -> PendingStep.SECOND_FACTOR_ENROLLMENT;
        };
        request.getSession(true).setAttribute(PENDING_SIGN_IN,
                new PendingSignIn(outcome.userUuid(), step, Instant.now(clock).plus(PENDING_SIGN_IN_LIFETIME)));
        return StepResponse.pending(step);
    }

    private PendingSignIn require(HttpServletRequest request, PendingStep step, Supplier<LoginException> notPending) {
        return pending(request).filter(sign -> sign.step() == step).orElseThrow(notPending);
    }

    private Optional<PendingSignIn> pending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object pending = session == null ? null : session.getAttribute(PENDING_SIGN_IN);
        return pending instanceof PendingSignIn sign && Instant.now(clock).isBefore(sign.expiresAt())
                ? Optional.of(sign)
                : Optional.empty();
    }

    private String establish(StaffIdentity identity, HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = requestCache.getRequest(request, response);
        Instant sessionStartedAt = SecurityContextHolder.getContext().getAuthentication() instanceof StaffAuthentication current
                && current.getPrincipal().uuid().equals(identity.uuid())
                ? current.getPrincipal().sessionStartedAt()
                : identity.authenticatedAt();
        HttpSession session = request.getSession(false);
        if (session == null) {
            session = request.getSession(true);
        } else {
            request.changeSessionId();
        }
        session.removeAttribute(PENDING_SIGN_IN);
        session.removeAttribute(StepUpFilter.STEP_UP_REQUIRED);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new StaffAuthentication(StaffPrincipal.of(identity, sessionStartedAt)));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        requestCache.removeRequest(request, response);
        return saved == null ? properties.homeUrl() : saved.getRedirectUrl();
    }
}
