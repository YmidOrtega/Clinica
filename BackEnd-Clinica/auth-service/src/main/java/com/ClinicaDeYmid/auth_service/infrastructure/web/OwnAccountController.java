package com.ClinicaDeYmid.auth_service.infrastructure.web;

import com.ClinicaDeYmid.auth_service.application.account.OwnAccount;
import com.ClinicaDeYmid.auth_service.application.admin.UserDirectory;
import com.ClinicaDeYmid.auth_service.application.session.StaffSessions;
import com.ClinicaDeYmid.auth_service.infrastructure.security.StaffPrincipal;
import com.ClinicaDeYmid.auth_service.infrastructure.web.UserViews.UserView;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/me")
class OwnAccountController {

    private final OwnAccount account;
    private final UserDirectory directory;

    OwnAccountController(OwnAccount account, UserDirectory directory) {
        this.account = account;
        this.directory = directory;
    }

    record ProfileView(UserView user, Instant authenticatedAt, List<String> methods, int remainingRecoveryCodes) {
    }

    record PasswordChange(String currentPassword, String newPassword) {
    }

    record RecoveryCodesView(List<String> recoveryCodes) {
    }

    record SessionView(String id, String clientId, Instant startedAt, Instant lastRefreshedAt, Instant expiresAt, boolean current) {

        static SessionView from(OwnAccount.OwnSession own) {
            StaffSessions.StaffSession session = own.session();
            return new SessionView(session.id(), session.clientId(), session.startedAt(), session.lastRefreshedAt(), session.expiresAt(), own.current());
        }
    }

    @GetMapping
    ProfileView profile(@AuthenticationPrincipal StaffPrincipal principal) {
        OwnAccount.Profile profile = account.profile(principal.caller());
        return new ProfileView(UserView.from(directory.details(profile.user())), principal.authenticatedAt(), principal.methods(),
                profile.remainingRecoveryCodes());
    }

    @PutMapping("/password")
    ResponseEntity<Void> changePassword(@AuthenticationPrincipal StaffPrincipal principal, @RequestBody PasswordChange body) {
        account.changePassword(principal.caller(), body.currentPassword(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recovery-codes")
    RecoveryCodesView regenerateRecoveryCodes(@AuthenticationPrincipal StaffPrincipal principal) {
        return new RecoveryCodesView(account.regenerateRecoveryCodes(principal.caller()));
    }

    @GetMapping("/sessions")
    List<SessionView> sessions(@AuthenticationPrincipal StaffPrincipal principal, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return account.sessions(principal.caller(), authorization.substring("Bearer ".length())).stream().map(SessionView::from).toList();
    }

    @DeleteMapping("/sessions/{id}")
    ResponseEntity<Void> closeSession(@AuthenticationPrincipal StaffPrincipal principal, @PathVariable String id) {
        account.closeSession(principal.caller(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/session-revocation")
    ResponseEntity<Void> signOutEverywhere(@AuthenticationPrincipal StaffPrincipal principal) {
        account.signOutEverywhere(principal.caller());
        return ResponseEntity.noContent().build();
    }
}
