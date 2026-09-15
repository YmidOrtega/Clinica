package com.ClinicaDeYmid.auth_service;

import com.ClinicaDeYmid.auth_service.domain.user.Role;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.support.AuthTestSupport;
import com.ClinicaDeYmid.auth_service.support.BearerTokens;
import com.ClinicaDeYmid.auth_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.auth_service.support.OAuthBrowser;
import com.ClinicaDeYmid.auth_service.support.StaffAccounts;
import com.ClinicaDeYmid.auth_service.support.StaffApi;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({MySqlTestContainer.class, AuthTestSupport.class, StaffAccounts.class})
class OwnAccountApiIT {

    @LocalServerPort
    private int port;

    @Autowired
    private StaffAccounts staff;

    @Autowired
    private BearerTokens tokens;

    private StaffApi api;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        AuthTestSupport.register(registry);
    }

    @BeforeEach
    void client() {
        api = new StaffApi(port);
    }

    @Test
    void everyStaffMemberSeesTheirOwnAccount() {
        StaffAccounts.StaffAccount nurse = staff.active(Role.NURSE);

        JsonNode profile = api.get("/api/v1/me", tokens.fresh(nurse.user())).json();

        assertThat(profile.get("user").get("email").asText()).isEqualTo(nurse.email());
        assertThat(profile.get("user").get("secondFactor").get("state").asText()).isEqualTo("TOTP_ENROLLED");
        assertThat(profile.get("remainingRecoveryCodes").asInt()).isEqualTo(10);
        assertThat(profile.get("methods")).extracting(JsonNode::asText).containsExactly("pwd", "otp", "mfa");
    }

    @Test
    void changingThePasswordNeedsTheCurrentOne() {
        User doctor = staff.active(Role.DOCTOR).user();
        String token = tokens.fresh(doctor);
        assertThat(api.put("/api/v1/me/password", tokens.authenticatedAgo(doctor, Duration.ofMinutes(6)), null,
                Map.of("currentPassword", StaffAccounts.PASSWORD, "newPassword", "una frase nueva para el turno")).status()).isEqualTo(401);

        OAuthBrowser.Response wrong = api.put("/api/v1/me/password", token, null,
                Map.of("currentPassword", "no es la contraseña", "newPassword", "una frase nueva para el turno"));
        assertThat(wrong.status()).isEqualTo(400);
        assertThat(wrong.json().get("code").asText()).isEqualTo("CURRENT_PASSWORD_INVALID");
        assertThat(api.put("/api/v1/me/password", token, null,
                Map.of("currentPassword", StaffAccounts.PASSWORD, "newPassword", "12345678")).json().get("code").asText())
                .isEqualTo("PASSWORD_REJECTED");

        assertThat(api.put("/api/v1/me/password", token, null,
                Map.of("currentPassword", StaffAccounts.PASSWORD, "newPassword", "una frase nueva para el turno")).status()).isEqualTo(204);

        assertThat(new OAuthBrowser(port).login(doctor.email().value(), StaffAccounts.PASSWORD).status()).isEqualTo(401);
        assertThat(new OAuthBrowser(port).login(doctor.email().value(), "una frase nueva para el turno").status()).isEqualTo(200);
    }

    @Test
    void regeneratingRecoveryCodesNeedsARecentSecondFactorAndInvalidatesTheOldOnes() {
        StaffAccounts.StaffAccount receptionist = staff.active(Role.RECEPTIONIST);

        assertThat(api.post("/api/v1/me/recovery-codes", tokens.authenticatedAgo(receptionist.user(), Duration.ofMinutes(6)), null).status())
                .isEqualTo(401);

        OAuthBrowser.Response regenerated = api.post("/api/v1/me/recovery-codes", tokens.fresh(receptionist.user()), null);

        assertThat(regenerated.status()).isEqualTo(200);
        assertThat(regenerated.json().get("recoveryCodes")).hasSize(10);
        OAuthBrowser browser = new OAuthBrowser(port);
        browser.login(receptionist.email(), StaffAccounts.PASSWORD);
        assertThat(browser.postJson("/api/v1/login/second-factor", Map.of("recoveryCode", receptionist.recoveryCodes().getFirst()), true).status())
                .isEqualTo(401);
        assertThat(browser.postJson("/api/v1/login/second-factor", Map.of("recoveryCode", regenerated.json().get("recoveryCodes").get(0).asText()),
                true).status()).isEqualTo(200);
    }

    @Test
    void staffSeeTheirSessionsAndCloseOneThatIsNotTheirs() {
        StaffAccounts.StaffAccount nurse = staff.active(Role.NURSE);
        OAuthBrowser laptop = new OAuthBrowser(port);
        laptop.authorize();
        JsonNode laptopTokens = laptop.exchangeCode(laptop.authorizationCode(laptop.signIn(nurse))).json();
        OAuthBrowser phone = new OAuthBrowser(port);
        phone.authorize();
        phone.login(nurse.email(), StaffAccounts.PASSWORD);
        String phoneContinue = phone.postJson("/api/v1/login/second-factor", Map.of("recoveryCode", nurse.recoveryCodes().getFirst()), true)
                .json().get("continueUrl").asText();
        JsonNode phoneTokens = phone.exchangeCode(phone.authorizationCode(phoneContinue)).json();
        String laptopAccess = laptopTokens.get("access_token").asText();

        JsonNode sessions = api.get("/api/v1/me/sessions", laptopAccess).json();

        assertThat(sessions).hasSize(2);
        assertThat(sessions).extracting(session -> session.get("current").asBoolean()).containsExactlyInAnyOrder(true, false);
        assertThat(sessions.get(0).get("clientId").asText()).isEqualTo("api-gateway");
        String phoneSession = sessions.get(0).get("current").asBoolean() ? sessions.get(1).get("id").asText() : sessions.get(0).get("id").asText();
        assertThat(api.delete("/api/v1/me/sessions/" + phoneSession, laptopAccess).status()).isEqualTo(204);
        assertThat(phone.refresh(phoneTokens.get("refresh_token").asText()).status()).isEqualTo(400);
        assertThat(laptop.refresh(laptopTokens.get("refresh_token").asText()).status()).isEqualTo(200);
        assertThat(api.delete("/api/v1/me/sessions/" + phoneSession, laptopAccess).status()).isEqualTo(404);
    }

    @Test
    void signingOutEverywhereRevokesEverySession() {
        StaffAccounts.StaffAccount medicalRecords = staff.active(Role.MEDICAL_RECORDS);
        OAuthBrowser browser = new OAuthBrowser(port);
        browser.authorize();
        JsonNode issued = browser.exchangeCode(browser.authorizationCode(browser.signIn(medicalRecords))).json();

        assertThat(api.post("/api/v1/me/session-revocation", issued.get("access_token").asText(), null).status()).isEqualTo(204);

        assertThat(browser.refresh(issued.get("refresh_token").asText()).status()).isEqualTo(400);
        assertThat(browser.get("/api/v1/session").json().get("authenticated").asBoolean()).isFalse();
        assertThat(api.get("/api/v1/me", tokens.issue(medicalRecords.user(), claims -> claims.issuedAt(Instant.now().minusSeconds(5)))).status())
                .isEqualTo(401);
    }

    @Test
    void tokensForAnotherAudienceOrIssuerAreRejected() {
        User nurse = staff.active(Role.NURSE).user();

        assertThat(api.get("/api/v1/me", tokens.issue(nurse, claims -> claims.audience(List.of("api-gateway")))).status()).isEqualTo(401);
        assertThat(api.get("/api/v1/me", tokens.issue(nurse, claims -> claims.issuer("http://otro.emisor"))).status()).isEqualTo(401);
    }
}
