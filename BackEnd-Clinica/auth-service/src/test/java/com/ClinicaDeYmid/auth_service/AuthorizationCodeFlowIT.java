package com.ClinicaDeYmid.auth_service;

import com.ClinicaDeYmid.auth_service.domain.user.Role;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.support.AuthTestSupport;
import com.ClinicaDeYmid.auth_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.auth_service.support.OAuthBrowser;
import com.ClinicaDeYmid.auth_service.support.StaffAccounts;
import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({MySqlTestContainer.class, AuthTestSupport.class, StaffAccounts.class})
class AuthorizationCodeFlowIT {

    @LocalServerPort
    private int port;

    @Autowired
    private StaffAccounts staff;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        AuthTestSupport.register(registry);
    }

    @Test
    void aClinicianSignsInWithPkceAndGetsEs256TokensSignedInsideOpenBao() throws Exception {
        StaffAccounts.StaffAccount account = staff.active(Role.DOCTOR);
        User doctor = account.user();
        OAuthBrowser browser = new OAuthBrowser(port);

        OAuthBrowser.Response unauthenticated = browser.authorize();
        assertThat(unauthenticated.status()).isEqualTo(302);
        assertThat(unauthenticated.location()).startsWith(AuthTestSupport.LOGIN_URL);

        OAuthBrowser.Response login = browser.login(doctor.email().value(), StaffAccounts.PASSWORD);
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.json().get("outcome").asText()).isEqualTo("SECOND_FACTOR_REQUIRED");
        assertThat(browser.authorize().location()).startsWith(AuthTestSupport.LOGIN_URL);
        OAuthBrowser.Response verified = browser.postJson("/api/v1/login/second-factor", Map.of("code", account.totpCode()), true);
        assertThat(verified.json().get("outcome").asText()).isEqualTo("AUTHENTICATED");
        String continueUrl = verified.json().get("continueUrl").asText();
        assertThat(continueUrl).contains("/oauth2/authorize");

        OAuthBrowser.Response tokens = browser.exchangeCode(browser.authorizationCode(continueUrl));
        assertThat(tokens.status()).isEqualTo(200);
        JsonNode body = tokens.json();
        SignedJWT accessToken = SignedJWT.parse(body.get("access_token").asText());
        JWKSet jwks = JWKSet.parse(browser.get("/oauth2/jwks").body());

        assertThat(accessToken.getHeader().getAlgorithm().getName()).isEqualTo("ES256");
        assertThat(accessToken.getHeader().getKeyID()).isEqualTo(AuthTestSupport.SIGNING_KEY + "-v1");
        assertThat(accessToken.verify(new ECDSAVerifier(jwks.getKeyByKeyId(accessToken.getHeader().getKeyID()).toECKey()))).isTrue();
        assertThat(jwks.getKeys()).allSatisfy(key -> assertThat(key.isPrivate()).isFalse());
        var claims = accessToken.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo(AuthTestSupport.ISSUER);
        assertThat(claims.getSubject()).isEqualTo(doctor.uuid().toString());
        assertThat(claims.getAudience()).containsExactly("clinica-api");
        assertThat(claims.getStringClaim("role")).isEqualTo("DOCTOR");
        assertThat(claims.getStringClaim("email")).isEqualTo(doctor.email().value());
        assertThat(claims.getStringListClaim("amr")).containsExactly("pwd", "otp", "mfa");
        assertThat(claims.getStringClaim("acr")).isEqualTo("urn:clinica:acr:mfa");
        assertThat(claims.getLongClaim("auth_time")).isLessThanOrEqualTo(claims.getIssueTime().getTime() / 1000);
        assertThat(Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant())).isEqualTo(Duration.ofMinutes(5));
        assertThat(SignedJWT.parse(body.get("id_token").asText()).getJWTClaimsSet().getStringClaim("name")).isEqualTo("Laura Gómez");

        List<String> storedHashes = jdbc.queryForList("SELECT value_hash FROM auth_sessions.authorization_tokens", String.class);
        assertThat(storedHashes).contains(sha256(body.get("access_token").asText()), sha256(body.get("refresh_token").asText()));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM auth_sessions.authorizations
                WHERE attributes LIKE ? OR principal LIKE ?""", Long.class,
                "%" + body.get("refresh_token").asText() + "%", "%" + body.get("access_token").asText() + "%")).isZero();
    }

    @Test
    void refreshTokensRotateAndReusingARotatedOneRevokesTheWholeFamily() {
        StaffAccounts.StaffAccount nurse = staff.active(Role.NURSE);
        OAuthBrowser browser = new OAuthBrowser(port);
        browser.authorize();
        String continueUrl = browser.signIn(nurse);
        JsonNode first = browser.exchangeCode(browser.authorizationCode(continueUrl)).json();

        OAuthBrowser.Response rotated = browser.refresh(first.get("refresh_token").asText());
        assertThat(rotated.status()).isEqualTo(200);
        String secondRefresh = rotated.json().get("refresh_token").asText();
        assertThat(secondRefresh).isNotEqualTo(first.get("refresh_token").asText());

        OAuthBrowser.Response reused = browser.refresh(first.get("refresh_token").asText());
        assertThat(reused.status()).isEqualTo(400);
        assertThat(reused.json().get("error").asText()).isEqualTo("invalid_grant");
        assertThat(browser.refresh(secondRefresh).status()).isEqualTo(400);
    }

    @Test
    void suspendingAUserStopsItsRefreshTokens() {
        StaffAccounts.StaffAccount receptionist = staff.active(Role.RECEPTIONIST);
        OAuthBrowser browser = new OAuthBrowser(port);
        browser.authorize();
        String continueUrl = browser.signIn(receptionist);
        String refresh = browser.exchangeCode(browser.authorizationCode(continueUrl)).json().get("refresh_token").asText();

        staff.update(receptionist.user(), user -> user.suspend("Suspensión preventiva por auditoría", StaffAccounts.provisioner(), clock));

        OAuthBrowser.Response refused = browser.refresh(refresh);
        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.json().get("error").asText()).isEqualTo("invalid_grant");
    }

    @Test
    void tokenRequestsNeedAnAssertionSignedByTheClientKey() {
        OAuthBrowser.Response forged = new OAuthBrowser(port).token(Map.of("grant_type", "refresh_token", "refresh_token", "x",
                "client_assertion", OAuthBrowser.clientAssertion().replaceAll("\\.[^.]+$", ".AAAA")));

        assertThat(forged.status()).isEqualTo(401);
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
