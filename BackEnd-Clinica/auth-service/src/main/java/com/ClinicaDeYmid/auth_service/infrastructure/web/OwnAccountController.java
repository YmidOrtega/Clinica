package com.ClinicaDeYmid.auth_service.infrastructure.web;

import com.ClinicaDeYmid.auth_service.application.account.OwnAccount;
import com.ClinicaDeYmid.auth_service.application.admin.UserDirectory;
import com.ClinicaDeYmid.auth_service.infrastructure.security.StaffPrincipal;
import com.ClinicaDeYmid.auth_service.infrastructure.web.UserViews.UserView;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/session-revocation")
    ResponseEntity<Void> signOutEverywhere(@AuthenticationPrincipal StaffPrincipal principal) {
        account.signOutEverywhere(principal.caller());
        return ResponseEntity.noContent().build();
    }
}
