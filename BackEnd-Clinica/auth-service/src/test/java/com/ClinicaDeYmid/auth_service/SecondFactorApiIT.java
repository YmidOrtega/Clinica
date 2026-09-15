package com.ClinicaDeYmid.auth_service;

import com.ClinicaDeYmid.auth_service.domain.user.Role;
import com.ClinicaDeYmid.auth_service.support.AuthTestSupport;
import com.ClinicaDeYmid.auth_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.auth_service.support.OAuthBrowser;
import com.ClinicaDeYmid.auth_service.support.StaffAccounts;
import com.nimbusds.jwt.SignedJWT;
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
class SecondFactorApiIT {

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
    void theSecondFactorIsRequiredAndCannotBeSkipped() {
        StaffAccounts.StaffAccount nurse = staff.active(Role.NURSE);
        OAuthBrowser browser = new OAuthBrowser(port);

        assertThat(browser.login(nurse.email(), StaffAccounts.PASSWORD).json().get("outcome").asText()).isEqualTo("SECOND_FACTOR_REQUIRED");

        assertThat(browser.get("/api/v1/session").json().get("authenticated").asBoolean()).isFalse();
        assertThat(browser.postJson("/api/v1/login/second-factor/enrollment", Map.of(), true).status()).isEqualTo(409);
        assertThat(new OAuthBrowser(port).postJson("/api/v1/login/second-factor", Map.of("code", nurse.totpCode()), true).status())
                .isEqualTo(409);
    }

    @Test
    void wrongCodesAreRejectedAndThrottled() {
        StaffAccounts.StaffAccount doctor = staff.active(Role.DOCTOR);
        OAuthBrowser browser = new OAuthBrowser(port);
        browser.login(doctor.email(), StaffAccounts.PASSWORD);

        for (int attempt = 0; attempt < 5; attempt++) {
            OAuthBrowser.Response wrong = browser.postJson("/api/v1/login/second-factor", Map.of("code", "00000" + attempt), true);
            assertThat(wrong.status()).isEqualTo(401);
            assertThat(wrong.json().get("code").asText()).isEqualTo("INVALID_SECOND_FACTOR");
        }

        OAuthBrowser.Response delayed = browser.postJson("/api/v1/login/second-factor", Map.of("code", doctor.totpCode()), true);
        assertThat(delayed.status()).isEqualTo(429);
    }

    @Test
    void aCodeWorksOnlyOnceAndRecoveryCodesAreSingleUse() {
        StaffAccounts.StaffAccount receptionist = staff.active(Role.RECEPTIONIST);
        String code = receptionist.totpCode();
        OAuthBrowser first = new OAuthBrowser(port);
        first.login(receptionist.email(), StaffAccounts.PASSWORD);
        assertThat(first.postJson("/api/v1/login/second-factor", Map.of("code", code), true).status()).isEqualTo(200);

        OAuthBrowser replay = new OAuthBrowser(port);
        replay.login(receptionist.email(), StaffAccounts.PASSWORD);
        assertThat(replay.postJson("/api/v1/login/second-factor", Map.of("code", code), true).status()).isEqualTo(401);

        String recoveryCode = receptionist.recoveryCodes().getFirst();
        OAuthBrowser.Response recovered = replay.postJson("/api/v1/login/second-factor", Map.of("recoveryCode", recoveryCode.toLowerCase()), true);
        assertThat(recovered.status()).isEqualTo(200);
        assertThat(recovered.json().get("remainingRecoveryCodes").asInt()).isEqualTo(9);

        OAuthBrowser reuse = new OAuthBrowser(port);
        reuse.login(receptionist.email(), StaffAccounts.PASSWORD);
        assertThat(reuse.postJson("/api/v1/login/second-factor", Map.of("recoveryCode", recoveryCode), true).status()).isEqualTo(401);
    }

    @Test
    void anAdministratorResetSendsTheUserBackToEnrollment() {
        StaffAccounts.StaffAccount nurse = staff.active(Role.NURSE);
        staff.update(nurse.user(), user -> user.resetSecondFactor("Pérdida del teléfono con la aplicación", StaffAccounts.provisioner(), clock));
        OAuthBrowser browser = new OAuthBrowser(port);

        assertThat(browser.login(nurse.email(), StaffAccounts.PASSWORD).json().get("outcome").asText())
                .isEqualTo("SECOND_FACTOR_ENROLLMENT_REQUIRED");
        assertThat(browser.get("/api/v1/session").json().get("pendingStep").asText()).isEqualTo("SECOND_FACTOR_ENROLLMENT");
        assertThat(browser.postJson("/api/v1/login/second-factor/enrollment", Map.of(), true).json().get("qrPngBase64").asText()).isNotBlank();
    }

    @Test
    void aStaleSessionMustStepUpBeforeAuthorizingAndTheTokenCarriesTheNewAuthTime() throws Exception {
        StaffAccounts.StaffAccount doctor = staff.active(Role.DOCTOR);
        OAuthBrowser browser = new OAuthBrowser(port);
        browser.authorize();
        String firstCode = browser.authorizationCode(browser.signIn(doctor));
        long firstAuthTime = SignedJWT.parse(browser.exchangeCode(firstCode).json().get("access_token").asText())
                .getJWTClaimsSet().getLongClaim("auth_time");
        Thread.sleep(2_100);

        OAuthBrowser.Response stepUp = browser.get(browser.authorizationUrl() + "&max_age=1");
        assertThat(stepUp.status()).isEqualTo(302);
        assertThat(stepUp.location()).startsWith(AuthTestSupport.LOGIN_URL).contains("step=step-up");
        assertThat(browser.get("/api/v1/session").json().get("pendingStep").asText()).isEqualTo("STEP_UP");

        OAuthBrowser.Response confirmed = browser.postJson("/api/v1/login/step-up", Map.of("recoveryCode", doctor.recoveryCodes().get(1)), true);
        assertThat(confirmed.status()).isEqualTo(200);
        String continueUrl = confirmed.json().get("continueUrl").asText();
        assertThat(continueUrl).contains("max_age=1");
        var claims = SignedJWT.parse(browser.exchangeCode(browser.authorizationCode(continueUrl)).json().get("access_token").asText())
                .getJWTClaimsSet();

        assertThat(claims.getLongClaim("auth_time")).isGreaterThan(firstAuthTime);
        assertThat(claims.getStringListClaim("amr")).containsExactly("pwd", "rec", "mfa");
        assertThat(browser.get("/api/v1/session").json().get("pendingStep").isNull()).isTrue();
    }

    @Test
    void stepUpNeedsASignedInSession() {
        OAuthBrowser.Response anonymous = new OAuthBrowser(port).postJson("/api/v1/login/step-up", Map.of("code", "123456"), true);

        assertThat(anonymous.status()).isEqualTo(401);
    }
}
