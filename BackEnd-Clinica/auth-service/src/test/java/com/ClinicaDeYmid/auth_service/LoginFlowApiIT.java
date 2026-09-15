package com.ClinicaDeYmid.auth_service;

import com.ClinicaDeYmid.auth_service.domain.user.Role;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.support.AuthTestSupport;
import com.ClinicaDeYmid.auth_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.auth_service.support.OAuthBrowser;
import com.ClinicaDeYmid.auth_service.support.StaffAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Clock;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({MySqlTestContainer.class, AuthTestSupport.class, StaffAccounts.class})
class LoginFlowApiIT {

    @LocalServerPort
    private int port;

    @Autowired
    private StaffAccounts staff;

    @Autowired
    private Clock clock;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        AuthTestSupport.register(registry);
    }

    @Test
    void theLoginApiRequiresTheSessionCsrfToken() {
        User nurse = staff.active(Role.NURSE).user();

        OAuthBrowser.Response withoutCsrf = new OAuthBrowser(port)
                .postJson("/api/v1/login", Map.of("email", nurse.email().value(), "password", StaffAccounts.PASSWORD), false);

        assertThat(withoutCsrf.status()).isEqualTo(403);
    }

    @Test
    void unknownEmailsWrongPasswordsAndSuspendedUsersGetTheSameAnswer() {
        User suspended = staff.active(Role.DOCTOR).user();
        staff.update(suspended, user -> user.suspend("Suspensión preventiva por auditoría", StaffAccounts.provisioner(), clock));
        User doctor = staff.active(Role.DOCTOR).user();
        OAuthBrowser browser = new OAuthBrowser(port);

        OAuthBrowser.Response unknown = browser.login("nadie@clinica.test", StaffAccounts.PASSWORD);
        OAuthBrowser.Response wrong = browser.login(doctor.email().value(), "otra frase distinta de verdad");
        OAuthBrowser.Response blocked = browser.login(suspended.email().value(), StaffAccounts.PASSWORD);

        assertThat(unknown.status()).isEqualTo(401);
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(blocked.status()).isEqualTo(401);
        assertThat(unknown.json().get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(wrong.json().get("detail")).isEqualTo(unknown.json().get("detail"));
        assertThat(blocked.json().get("detail")).isEqualTo(unknown.json().get("detail"));
    }

    @Test
    void repeatedFailuresFromTheSameAddressAreDelayed() {
        User receptionist = staff.active(Role.RECEPTIONIST).user();
        OAuthBrowser browser = new OAuthBrowser(port);
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(browser.login(receptionist.email().value(), "intento equivocado número " + attempt).status()).isEqualTo(401);
        }

        OAuthBrowser.Response delayed = browser.login(receptionist.email().value(), StaffAccounts.PASSWORD);

        assertThat(delayed.status()).isEqualTo(429);
        assertThat(delayed.raw().headers().firstValue("Retry-After")).contains("1");
    }

    @Test
    void aCompromisedPasswordMustBeReplacedBeforeTheSessionStarts() {
        StaffAccounts.StaffAccount account = staff.active(Role.DOCTOR);
        User doctor = account.user();
        staff.update(doctor, user -> user.requirePasswordChange("Contraseña expuesta en un correo", StaffAccounts.provisioner(), clock));
        OAuthBrowser browser = new OAuthBrowser(port);
        browser.authorize();

        OAuthBrowser.Response login = browser.login(doctor.email().value(), StaffAccounts.PASSWORD);
        assertThat(login.json().get("outcome").asText()).isEqualTo("PASSWORD_CHANGE_REQUIRED");
        assertThat(browser.get("/api/v1/session").json().get("authenticated").asBoolean()).isFalse();

        OAuthBrowser.Response reused = browser.postJson("/api/v1/login/password-change", Map.of("newPassword", StaffAccounts.PASSWORD), true);
        assertThat(reused.status()).isEqualTo(400);
        assertThat(reused.json().get("code").asText()).isEqualTo("PASSWORD_REUSED");

        OAuthBrowser.Response changed = browser.postJson("/api/v1/login/password-change",
                Map.of("newPassword", "una nueva frase secreta para hoy"), true);
        assertThat(changed.status()).isEqualTo(200);
        assertThat(changed.json().get("outcome").asText()).isEqualTo("SECOND_FACTOR_REQUIRED");
        assertThat(browser.get("/api/v1/session").json().get("pendingStep").asText()).isEqualTo("SECOND_FACTOR");

        OAuthBrowser.Response verified = browser.postJson("/api/v1/login/second-factor", Map.of("code", account.totpCode()), true);
        assertThat(verified.json().get("continueUrl").asText()).contains("/oauth2/authorize");
        assertThat(browser.get("/api/v1/session").json().get("user").get("role").asText()).isEqualTo("DOCTOR");
    }

    @Test
    void loggingOutEndsTheSession() {
        StaffAccounts.StaffAccount nurse = staff.active(Role.NURSE);
        OAuthBrowser browser = new OAuthBrowser(port);
        browser.signIn(nurse);
        assertThat(browser.get("/api/v1/session").json().get("authenticated").asBoolean()).isTrue();

        assertThat(browser.postJson("/api/v1/logout", Map.of(), true).status()).isEqualTo(204);

        assertThat(browser.get("/api/v1/session").json().get("authenticated").asBoolean()).isFalse();
        assertThat(browser.authorize().location()).startsWith(AuthTestSupport.LOGIN_URL);
    }
}
