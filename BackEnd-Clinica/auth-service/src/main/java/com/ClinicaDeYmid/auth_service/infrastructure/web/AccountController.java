package com.ClinicaDeYmid.auth_service.infrastructure.web;

import com.ClinicaDeYmid.auth_service.application.account.AccountActivation;
import com.ClinicaDeYmid.auth_service.application.account.PasswordRecovery;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class AccountController {

    private final AccountActivation activation;
    private final PasswordRecovery recovery;

    AccountController(AccountActivation activation, PasswordRecovery recovery) {
        this.activation = activation;
        this.recovery = recovery;
    }

    record ActivationRequest(String token, String password) {
    }

    record ResetRequest(String email) {
    }

    record NewPasswordRequest(String token, String password) {
    }

    @PostMapping("/activation")
    ResponseEntity<Void> activate(@RequestBody ActivationRequest body) {
        activation.activate(body.token(), body.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/requests")
    ResponseEntity<Void> requestReset(@RequestBody ResetRequest body) {
        recovery.request(body.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset")
    ResponseEntity<Void> reset(@RequestBody NewPasswordRequest body) {
        recovery.reset(body.token(), body.password());
        return ResponseEntity.noContent().build();
    }
}
