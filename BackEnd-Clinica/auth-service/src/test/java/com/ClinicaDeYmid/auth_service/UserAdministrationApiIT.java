package com.ClinicaDeYmid.auth_service;

import com.ClinicaDeYmid.auth_service.application.Caller;
import com.ClinicaDeYmid.auth_service.application.admin.UserAdministration;
import com.ClinicaDeYmid.auth_service.domain.throttle.LoginThrottle;
import com.ClinicaDeYmid.auth_service.domain.throttle.ThrottleKey;
import com.ClinicaDeYmid.auth_service.domain.user.Actor;
import com.ClinicaDeYmid.auth_service.domain.user.Role;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.UserException;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({MySqlTestContainer.class, AuthTestSupport.class, StaffAccounts.class})
class UserAdministrationApiIT {

    private static final String REASON = "Revisión de accesos del trimestre";

    @LocalServerPort
    private int port;

    @Autowired
    private StaffAccounts staff;

    @Autowired
    private BearerTokens tokens;

    @Autowired
    private UserAdministration administration;

    @Autowired
    private LoginThrottle throttle;

    @Autowired
    private JdbcTemplate jdbc;

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
    void anAdministratorInvitesOperationalStaffButNotOtherAdministrators() {
        User admin = staff.active(Role.ADMIN).user();
        String token = tokens.fresh(admin);
        String email = "nueva." + UUID.randomUUID().toString().substring(0, 8) + "@clinica.test";

        OAuthBrowser.Response invited = api.post("/api/v1/users", token, Map.of("email", email, "fullName", "Sara Méndez", "role", "NURSE"));

        assertThat(invited.status()).isEqualTo(201);
        assertThat(invited.raw().headers().firstValue("ETag")).contains("\"0\"");
        JsonNode user = invited.json();
        assertThat(invited.location()).endsWith("/api/v1/users/" + user.get("uuid").asText());
        assertThat(user.get("status").get("code").asText()).isEqualTo("PENDING_ACTIVATION");
        assertThat(user.get("secondFactor").get("state").asText()).isEqualTo("NOT_ENROLLED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions.mail_outbox WHERE user_uuid = ? AND kind = 'ACTIVATION'", Long.class,
                user.get("uuid").asText())).isEqualTo(1);
        assertThat(api.post("/api/v1/users/" + user.get("uuid").asText() + "/invitation", token, null).status()).isEqualTo(202);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions.mail_outbox WHERE user_uuid = ?", Long.class,
                user.get("uuid").asText())).isEqualTo(1);

        JsonNode found = api.post("/api/v1/users/search", token, Map.of("text", "nueva." + email.substring(6, 10))).json();
        assertThat(found.get("content")).extracting(match -> match.get("email").asText()).contains(email);

        OAuthBrowser.Response privileged = api.post("/api/v1/users", token,
                Map.of("email", "otro." + email, "fullName", "Sara Méndez", "role", "ADMIN"));
        assertThat(privileged.status()).isEqualTo(403);
        assertThat(privileged.json().get("code").asText()).isEqualTo("USER_ROLE_NOT_MANAGEABLE");

        String doctorToken = tokens.fresh(staff.active(Role.DOCTOR).user());
        assertThat(api.post("/api/v1/users/search", doctorToken, Map.of()).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/users/" + admin.uuid(), null).status()).isEqualTo(401);
    }

    @Test
    void sensitiveChangesNeedASecondFactorVerifiedInTheLastFiveMinutes() {
        User admin = staff.active(Role.ADMIN).user();
        User nurse = staff.active(Role.NURSE).user();
        String stale = tokens.authenticatedAgo(admin, Duration.ofMinutes(6));

        OAuthBrowser.Response refused = api.post("/api/v1/users/" + nurse.uuid() + "/suspension", stale, tag(nurse), Map.of("reason", REASON));

        assertThat(refused.status()).isEqualTo(401);
        assertThat(refused.json().get("code").asText()).isEqualTo("STEP_UP_REQUIRED");
        assertThat(refused.json().get("maxAge").asInt()).isEqualTo(300);
        assertThat(refused.raw().headers().firstValue("WWW-Authenticate").orElseThrow())
                .contains("error=\"insufficient_user_authentication\"").contains("max_age=300");
        String passwordOnly = tokens.issue(admin, claims -> claims.claim("amr", List.of("pwd")));
        assertThat(api.post("/api/v1/users/" + nurse.uuid() + "/suspension", passwordOnly, tag(nurse), Map.of("reason", REASON)).status())
                .isEqualTo(401);

        OAuthBrowser.Response renamed = api.put("/api/v1/users/" + nurse.uuid() + "/name", stale, tag(nurse), Map.of("fullName", "Laura Gómez Ruiz"));
        assertThat(renamed.status()).isEqualTo(200);
        assertThat(renamed.json().get("fullName").asText()).isEqualTo("Laura Gómez Ruiz");
    }

    @Test
    void changesRequireTheVersionTheAdministratorSaw() {
        String token = tokens.fresh(staff.active(Role.ADMIN).user());
        User receptionist = staff.active(Role.RECEPTIONIST).user();
        String path = "/api/v1/users/" + receptionist.uuid() + "/role";

        assertThat(api.put(path, token, null, Map.of("role", "NURSE")).status()).isEqualTo(428);
        assertThat(api.put(path, token, "\"" + (receptionist.version() + 5) + "\"", Map.of("role", "NURSE")).status()).isEqualTo(412);

        OAuthBrowser.Response changed = api.put(path, token, tag(receptionist), Map.of("role", "NURSE"));
        assertThat(changed.status()).isEqualTo(200);
        assertThat(changed.json().get("role").asText()).isEqualTo("NURSE");
        assertThat(changed.raw().headers().firstValue("ETag")).contains("\"" + (receptionist.version() + 1) + "\"");
    }

    @Test
    void suspendingAUserRevokesItsTokensAndIsRecordedInTheHistory() {
        User admin = staff.active(Role.ADMIN).user();
        StaffAccounts.StaffAccount nurse = staff.active(Role.NURSE);
        OAuthBrowser browser = new OAuthBrowser(port);
        browser.authorize();
        JsonNode nurseTokens = browser.exchangeCode(browser.authorizationCode(browser.signIn(nurse))).json();
        String nurseAccess = nurseTokens.get("access_token").asText();
        assertThat(api.get("/api/v1/me", nurseAccess).status()).isEqualTo(200);

        OAuthBrowser.Response suspended = api.post("/api/v1/users/" + nurse.user().uuid() + "/suspension", tokens.fresh(admin), tag(nurse.user()),
                Map.of("reason", REASON));

        assertThat(suspended.status()).isEqualTo(200);
        assertThat(suspended.json().get("status").get("reason").asText()).isEqualTo(REASON);
        assertThat(suspended.json().get("status").get("changedBy").asText()).isEqualTo(admin.uuid().toString());
        assertThat(api.get("/api/v1/me", nurseAccess).status()).isEqualTo(401);
        assertThat(browser.refresh(nurseTokens.get("refresh_token").asText()).status()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions.authorizations WHERE principal_name = ?", Long.class,
                nurse.user().uuid().toString())).isZero();

        JsonNode history = api.get("/api/v1/users/" + nurse.user().uuid() + "/history", tokens.fresh(admin)).json();
        JsonNode last = history.get(history.size() - 1);
        assertThat(last.get("status").get("code").asText()).isEqualTo("SUSPENDED");
        assertThat(last.get("revisedBy").asText()).isEqualTo(admin.uuid().toString());
    }

    @Test
    void anAdministratorClosesEverySessionOfAStaffMember() {
        User admin = staff.active(Role.ADMIN).user();
        StaffAccounts.StaffAccount receptionist = staff.active(Role.RECEPTIONIST);
        OAuthBrowser browser = new OAuthBrowser(port);
        browser.authorize();
        JsonNode issued = browser.exchangeCode(browser.authorizationCode(browser.signIn(receptionist))).json();

        assertThat(api.post("/api/v1/users/" + receptionist.user().uuid() + "/session-revocation", tokens.authenticatedAgo(admin, Duration.ofMinutes(6)),
                tag(receptionist.user()), null).status()).isEqualTo(401);
        OAuthBrowser.Response revoked = api.post("/api/v1/users/" + receptionist.user().uuid() + "/session-revocation", tokens.fresh(admin),
                tag(receptionist.user()), null);

        assertThat(revoked.status()).isEqualTo(200);
        assertThat(browser.refresh(issued.get("refresh_token").asText()).status()).isEqualTo(400);
        assertThat(api.get("/api/v1/me", issued.get("access_token").asText()).status()).isEqualTo(401);
        assertThat(api.post("/api/v1/users/" + admin.uuid() + "/session-revocation", tokens.fresh(staff.active(Role.ADMIN).user()), tag(admin), null)
                .status()).isEqualTo(403);
    }

    @Test
    void resettingTheSecondFactorSendsTheUserBackToEnrollment() {
        User superAdmin = staff.active(Role.SUPER_ADMIN).user();
        StaffAccounts.StaffAccount doctor = staff.active(Role.DOCTOR);

        OAuthBrowser.Response reset = api.post("/api/v1/users/" + doctor.user().uuid() + "/second-factor-reset", tokens.fresh(superAdmin), tag(doctor.user()),
                Map.of("reason", "Perdió el teléfono con la aplicación"));

        assertThat(reset.status()).isEqualTo(200);
        assertThat(reset.json().get("secondFactor").get("state").asText()).isEqualTo("NOT_ENROLLED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recovery_codes WHERE user_uuid = ? AND revoked_at IS NULL AND used_at IS NULL",
                Long.class, doctor.user().uuid().toString())).isZero();
        assertThat(new OAuthBrowser(port).login(doctor.email(), StaffAccounts.PASSWORD).json().get("outcome").asText())
                .isEqualTo("SECOND_FACTOR_ENROLLMENT_REQUIRED");
        assertThat(api.post("/api/v1/users/" + doctor.user().uuid() + "/second-factor-reset", tokens.fresh(superAdmin), reset.raw().headers().firstValue("ETag").orElseThrow(),
                Map.of("reason", "Perdió el teléfono con la aplicación")).json().get("code").asText()).isEqualTo("SECOND_FACTOR_NOT_ENROLLED");
    }

    @Test
    void anAdministratorUnlocksAnAccountLockedByRepeatedFailures() {
        String token = tokens.fresh(staff.active(Role.ADMIN).user());
        User receptionist = staff.active(Role.RECEPTIONIST).user();
        for (int failure = 0; failure < 100; failure++) {
            throttle.recordFailure(new ThrottleKey.Account(receptionist.email()), Instant.now());
        }
        assertThat(api.get("/api/v1/users/" + receptionist.uuid(), token).json().get("locked").asBoolean()).isTrue();
        assertThat(new OAuthBrowser(port).login(receptionist.email().value(), StaffAccounts.PASSWORD).status()).isEqualTo(423);

        OAuthBrowser.Response unlocked = api.post("/api/v1/users/" + receptionist.uuid() + "/unlock", token, null);

        assertThat(unlocked.status()).isEqualTo(200);
        assertThat(unlocked.json().get("locked").asBoolean()).isFalse();
        assertThat(new OAuthBrowser(port).login(receptionist.email().value(), StaffAccounts.PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void theClinicAlwaysKeepsAnActiveSuperAdmin() {
        jdbc.update("""
                UPDATE users SET status = 'SUSPENDED', status_reason = 'Aislado para la prueba', status_changed_by = uuid,
                                 status_changed_by_role = 'SUPER_ADMIN', status_changed_at = NOW(6)
                WHERE role = 'SUPER_ADMIN' AND status = 'ACTIVE'""");
        User first = staff.active(Role.SUPER_ADMIN).user();
        User second = staff.active(Role.SUPER_ADMIN).user();

        CompletableFuture<Boolean> firstSuspendsSecond = CompletableFuture.supplyAsync(() -> attempt(first, second));
        CompletableFuture<Boolean> secondSuspendsFirst = CompletableFuture.supplyAsync(() -> attempt(second, first));

        assertThat(List.of(firstSuspendsSecond.join(), secondSuspendsFirst.join())).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'SUPER_ADMIN' AND status = 'ACTIVE'", Long.class)).isEqualTo(1);
        String survivor = jdbc.queryForObject("SELECT uuid FROM users WHERE role = 'SUPER_ADMIN' AND status = 'ACTIVE'", String.class);
        long version = jdbc.queryForObject("SELECT version FROM users WHERE uuid = ?", Long.class, survivor);
        assertThatThrownBy(() -> administration.changeRole(superAdminCaller(UUID.randomUUID()), UUID.fromString(survivor), version, Role.ADMIN))
                .isInstanceOf(UserException.LastSuperAdmin.class);
    }

    private static String tag(User user) {
        return "\"" + user.version() + "\"";
    }

    private boolean attempt(User actor, User target) {
        try {
            administration.suspend(superAdminCaller(actor.uuid()), target.uuid(), target.version(), REASON);
            return true;
        } catch (UserException.LastSuperAdmin lastSuperAdmin) {
            return false;
        }
    }

    private static Caller superAdminCaller(UUID uuid) {
        return new Caller(new Actor(uuid, Role.SUPER_ADMIN), Instant.now(), true);
    }
}
