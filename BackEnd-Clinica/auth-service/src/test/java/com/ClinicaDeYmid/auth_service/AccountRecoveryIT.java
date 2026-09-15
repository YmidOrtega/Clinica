package com.ClinicaDeYmid.auth_service;

import com.ClinicaDeYmid.auth_service.application.mail.AccountMailing;
import com.ClinicaDeYmid.auth_service.domain.user.EmailAddress;
import com.ClinicaDeYmid.auth_service.domain.user.Role;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.UserStatus;
import com.ClinicaDeYmid.auth_service.domain.user.Users;
import com.ClinicaDeYmid.auth_service.support.AuthTestSupport;
import com.ClinicaDeYmid.auth_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.auth_service.support.OAuthBrowser;
import com.ClinicaDeYmid.auth_service.support.StaffAccounts;
import com.ClinicaDeYmid.commons.openbao.testing.TotpCodes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({MySqlTestContainer.class, AuthTestSupport.class, StaffAccounts.class})
class AccountRecoveryIT {

    private static final String FIRST_ADMIN = "primer.admin@clinica.test";

    @LocalServerPort
    private int port;

    @Autowired
    private Users users;

    @Autowired
    private StaffAccounts staff;

    @Autowired
    private AccountMailing mailing;

    @Autowired
    private AuthTestSupport.CapturingMailer mailer;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        AuthTestSupport.register(registry);
        registry.add("clinica.auth.bootstrap.super-admin-email", () -> FIRST_ADMIN);
        registry.add("clinica.auth.bootstrap.super-admin-name", () -> "Administradora Inicial");
    }

    @Test
    void theFirstSuperAdminIsInvitedAndActivatesThroughTheEmailedLink() {
        User invited = users.findByEmail(new EmailAddress(FIRST_ADMIN)).orElseThrow();
        assertThat(invited.role()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(invited.status()).isEqualTo(new UserStatus.PendingActivation());

        mailing.dispatchDue();
        String token = mailer.lastTokenSentTo(FIRST_ADMIN);
        OAuthBrowser browser = new OAuthBrowser(port);

        OAuthBrowser.Response weak = browser.postJson("/api/v1/activation", Map.of("token", token, "password", "password123"), true);
        assertThat(weak.status()).isEqualTo(400);
        assertThat(weak.json().get("code").asText()).isEqualTo("PASSWORD_REJECTED");

        assertThat(browser.postJson("/api/v1/activation", Map.of("token", token, "password", StaffAccounts.PASSWORD), true).status())
                .isEqualTo(204);
        assertThat(browser.postJson("/api/v1/activation", Map.of("token", token, "password", StaffAccounts.PASSWORD), true).status())
                .isEqualTo(400);
        assertThat(browser.login(FIRST_ADMIN, StaffAccounts.PASSWORD).json().get("outcome").asText())
                .isEqualTo("SECOND_FACTOR_ENROLLMENT_REQUIRED");
        OAuthBrowser.Response enrollment = browser.postJson("/api/v1/login/second-factor/enrollment", Map.of(), true);
        String otpauthUrl = enrollment.json().get("otpauthUrl").asText();
        assertThat(otpauthUrl).startsWith("otpauth://totp/").contains("secret=");
        OAuthBrowser.Response confirmed = browser.postJson("/api/v1/login/second-factor/enrollment/confirmation",
                Map.of("code", TotpCodes.at(otpauthUrl, Instant.now())), true);
        assertThat(confirmed.json().get("outcome").asText()).isEqualTo("AUTHENTICATED");
        assertThat(confirmed.json().get("recoveryCodes")).hasSize(10);
        assertThat(browser.get("/api/v1/session").json().get("user").get("role").asText()).isEqualTo("SUPER_ADMIN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions.one_time_tokens WHERE token_hash = ?", Long.class, token)).isZero();
    }

    @Test
    void resettingThePasswordRevokesEverySessionAndUnknownEmailsLookTheSame() {
        StaffAccounts.StaffAccount account = staff.active(Role.DOCTOR);
        User doctor = account.user();
        OAuthBrowser browser = new OAuthBrowser(port);
        browser.authorize();
        String continueUrl = browser.signIn(account);
        String refresh = browser.exchangeCode(browser.authorizationCode(continueUrl)).json().get("refresh_token").asText();

        OAuthBrowser anonymous = new OAuthBrowser(port);
        assertThat(anonymous.postJson("/api/v1/password-reset/requests", Map.of("email", "nadie@clinica.test"), true).status()).isEqualTo(202);
        assertThat(anonymous.postJson("/api/v1/password-reset/requests", Map.of("email", doctor.email().value()), true).status()).isEqualTo(202);
        mailing.dispatchDue();
        assertThat(mailer.sentTo("nadie@clinica.test")).isEmpty();
        String token = mailer.lastTokenSentTo(doctor.email().value());

        assertThat(anonymous.postJson("/api/v1/password-reset", Map.of("token", token, "password", "otra frase para el turno de noche"), true)
                .status()).isEqualTo(204);

        assertThat(browser.refresh(refresh).status()).isEqualTo(400);
        assertThat(browser.get("/api/v1/session").json().get("authenticated").asBoolean()).isFalse();
        assertThat(new OAuthBrowser(port).login(doctor.email().value(), StaffAccounts.PASSWORD).status()).isEqualTo(401);
        assertThat(new OAuthBrowser(port).login(doctor.email().value(), "otra frase para el turno de noche").json().get("outcome").asText())
                .isEqualTo("SECOND_FACTOR_REQUIRED");
    }
}
