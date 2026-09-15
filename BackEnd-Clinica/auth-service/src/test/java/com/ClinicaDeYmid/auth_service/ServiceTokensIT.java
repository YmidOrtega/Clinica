package com.ClinicaDeYmid.auth_service;

import com.ClinicaDeYmid.auth_service.domain.user.Role;
import com.ClinicaDeYmid.auth_service.support.AuthTestSupport;
import com.ClinicaDeYmid.auth_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.auth_service.support.OAuthBrowser;
import com.ClinicaDeYmid.auth_service.support.StaffAccounts;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({MySqlTestContainer.class, AuthTestSupport.class, StaffAccounts.class})
class ServiceTokensIT {

    private static final String TOKEN_EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";

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
    void aServiceGetsItsOwnTokenWithoutAnyStaffRole() throws ParseException {
        OAuthBrowser.Response issued = serviceToken(AuthTestSupport.CLINICAL_SERVICE, AuthTestSupport.CLINICAL_SERVICE_KEY);

        assertThat(issued.status()).isEqualTo(200);
        assertThat(issued.json().has("refresh_token")).isFalse();
        JWTClaimsSet claims = claims(issued);
        assertThat(claims.getSubject()).isEqualTo(AuthTestSupport.CLINICAL_SERVICE);
        assertThat(claims.getStringClaim("client_id")).isEqualTo(AuthTestSupport.CLINICAL_SERVICE);
        assertThat(claims.getAudience()).containsExactly("clinica-api");
        assertThat(claims.getClaim("role")).isNull();
        assertThat(Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant())).isEqualTo(Duration.ofMinutes(30));
        assertThat(new OAuthBrowser(port).tokenAs(AuthTestSupport.CLINICAL_SERVICE, AuthTestSupport.CLIENT_ASSERTION_KEY,
                Map.of("grant_type", "client_credentials")).status()).isEqualTo(401);
    }

    @Test
    void aServiceActsOnBehalfOfTheStaffMemberOnlyTowardsItsAllowedAudience() throws ParseException {
        StaffAccounts.StaffAccount doctor = staff.active(Role.DOCTOR);
        OAuthBrowser browser = new OAuthBrowser(port);
        browser.authorize();
        String subjectToken = browser.exchangeCode(browser.authorizationCode(browser.signIn(doctor))).json().get("access_token").asText();
        JWTClaimsSet subject = SignedJWT.parse(subjectToken).getJWTClaimsSet();
        String actorToken = serviceToken(AuthTestSupport.CLINICAL_SERVICE, AuthTestSupport.CLINICAL_SERVICE_KEY).json().get("access_token").asText();

        OAuthBrowser.Response exchanged = exchange(AuthTestSupport.CLINICAL_SERVICE, AuthTestSupport.CLINICAL_SERVICE_KEY, subjectToken, actorToken,
                AuthTestSupport.PATIENT_SERVICE);

        assertThat(exchanged.status()).as(exchanged.body()).isEqualTo(200);
        assertThat(exchanged.json().get("issued_token_type").asText()).isEqualTo(ACCESS_TOKEN_TYPE);
        JWTClaimsSet claims = claims(exchanged);
        assertThat(claims.getSubject()).isEqualTo(doctor.user().uuid().toString());
        assertThat(claims.getAudience()).containsExactly(AuthTestSupport.PATIENT_SERVICE);
        assertThat(claims.getStringClaim("role")).isEqualTo("DOCTOR");
        assertThat(claims.getLongClaim("auth_time")).isEqualTo(subject.getLongClaim("auth_time"));
        assertThat(claims.getStringListClaim("amr")).containsExactly("pwd", "otp", "mfa");
        assertThat(claims.getStringClaim("client_id")).isEqualTo(AuthTestSupport.CLINICAL_SERVICE);
        assertThat(claims.getJSONObjectClaim("act")).containsEntry("sub", AuthTestSupport.CLINICAL_SERVICE).containsEntry("iss", AuthTestSupport.ISSUER);
        assertThat(claims.getExpirationTime()).isBeforeOrEqualTo(subject.getExpirationTime());

        assertThat(exchange(AuthTestSupport.CLINICAL_SERVICE, AuthTestSupport.CLINICAL_SERVICE_KEY, subjectToken, actorToken, "billing-service")
                .json().get("error").asText()).isEqualTo("invalid_target");
        assertThat(exchange(AuthTestSupport.PATIENT_SERVICE, AuthTestSupport.PATIENT_SERVICE_KEY, subjectToken,
                serviceToken(AuthTestSupport.PATIENT_SERVICE, AuthTestSupport.PATIENT_SERVICE_KEY).json().get("access_token").asText(),
                AuthTestSupport.PATIENT_SERVICE).json().get("error").asText()).isEqualTo("invalid_target");
        assertThat(exchange(AuthTestSupport.CLINICAL_SERVICE, AuthTestSupport.CLINICAL_SERVICE_KEY, subjectToken, null, AuthTestSupport.PATIENT_SERVICE)
                .json().get("error").asText()).isEqualTo("invalid_request");
        assertThat(exchange(AuthTestSupport.CLIENT_ID, AuthTestSupport.CLIENT_ASSERTION_KEY, subjectToken, actorToken, AuthTestSupport.PATIENT_SERVICE)
                .json().get("error").asText()).isEqualTo("unauthorized_client");
    }

    @Test
    void aSuspendedStaffMemberCannotBeImpersonatedByAService() {
        StaffAccounts.StaffAccount nurse = staff.active(Role.NURSE);
        OAuthBrowser browser = new OAuthBrowser(port);
        browser.authorize();
        String subjectToken = browser.exchangeCode(browser.authorizationCode(browser.signIn(nurse))).json().get("access_token").asText();
        String actorToken = serviceToken(AuthTestSupport.CLINICAL_SERVICE, AuthTestSupport.CLINICAL_SERVICE_KEY).json().get("access_token").asText();

        staff.update(nurse.user(), user -> user.suspend("Suspensión preventiva por auditoría", StaffAccounts.provisioner(), clock));

        OAuthBrowser.Response refused = exchange(AuthTestSupport.CLINICAL_SERVICE, AuthTestSupport.CLINICAL_SERVICE_KEY, subjectToken, actorToken,
                AuthTestSupport.PATIENT_SERVICE);
        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.json().get("error").asText()).isEqualTo("invalid_grant");
    }

    private OAuthBrowser.Response serviceToken(String clientId, String key) {
        return new OAuthBrowser(port).tokenAs(clientId, key, Map.of("grant_type", "client_credentials"));
    }

    private OAuthBrowser.Response exchange(String clientId, String key, String subjectToken, String actorToken, String audience) {
        Map<String, String> grant = new HashMap<>(Map.of("grant_type", TOKEN_EXCHANGE, "subject_token", subjectToken,
                "subject_token_type", ACCESS_TOKEN_TYPE, "audience", audience));
        if (actorToken != null) {
            grant.put("actor_token", actorToken);
            grant.put("actor_token_type", ACCESS_TOKEN_TYPE);
        }
        return new OAuthBrowser(port).tokenAs(clientId, key, grant);
    }

    private static JWTClaimsSet claims(OAuthBrowser.Response response) throws ParseException {
        return SignedJWT.parse(response.json().get("access_token").asText()).getJWTClaimsSet();
    }
}
