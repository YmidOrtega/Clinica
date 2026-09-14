package com.ClinicaDeYmid.auth_service.infrastructure.persistence;

import com.ClinicaDeYmid.auth_service.support.MySqlTestContainer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

import static com.ClinicaDeYmid.auth_service.support.SecretFiles.withSecretFiles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class DatabaseAccessIT {

    private static final String MIGRATOR = "auth_migrator";
    private static final String MIGRATOR_PASSWORD = "migrator-test-secret";
    private static final String APP = "auth_app";
    private static final String APP_PASSWORD = "app-test-secret";
    private static final String HASH = "$argon2id$v=19$m=19456,t=2,p=1$c2FsdHNhbHRzYWx0c2FsdA$aGFzaGhhc2hoYXNoaGFzaGhhc2hoYXNoaGFzaGhhc2g";
    private static final String INSERT_USER = """
            INSERT INTO users (uuid, version, email, full_name, role, status, status_reason, status_changed_by, status_changed_by_role,
                               status_changed_at, password_hash, credential_state, credential_reason, password_changed_at,
                               tokens_not_before, created_at, updated_at)
            VALUES (?, 0, ?, 'Ana María Rojas', ?, ?, ?, ?, ?, NOW(6), ?, ?, NULL, ?, NOW(6), NOW(6), NOW(6))""";

    @Container
    static final MySQLContainer<?> MYSQL = withSecretFiles(new MySQLContainer<>(MySqlTestContainer.IMAGE)
            .withCopyFileToContainer(MountableFile.forHostPath("docker/mysql-init/01-create-users.sh", 0755),
                    "/docker-entrypoint-initdb.d/01-create-users.sh"), Map.of(
            "AUTH_DB_MIGRATOR_USER", MIGRATOR,
            "AUTH_DB_MIGRATOR_PASSWORD", MIGRATOR_PASSWORD,
            "AUTH_DB_APP_USER", APP,
            "AUTH_DB_APP_PASSWORD", APP_PASSWORD));

    private static JdbcTemplate app;

    @BeforeAll
    static void migrateWithTheMigratorUser() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MIGRATOR, MIGRATOR_PASSWORD)
                .locations("classpath:db/migration/auth")
                .load()
                .migrate();
        app = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), APP, APP_PASSWORD));
    }

    @BeforeEach
    void seedAnActiveUser() {
        new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()))
                .update("DELETE FROM users WHERE email = 'activa@clinica.test'");
        app.update(INSERT_USER, UUID.randomUUID().toString(), "activa@clinica.test", "NURSE", "ACTIVE", null, null, null, HASH, "CURRENT",
                Timestamp.valueOf("2026-09-14 10:00:00"));
    }

    @Test
    void applicationUserManagesUsersAndThrottlingButNeverDeletesUsers() {
        app.update("UPDATE users SET full_name = 'Ana Rojas' WHERE email = 'activa@clinica.test'");
        app.update("INSERT INTO auth_sessions.login_throttles VALUES (REPEAT('a', 64), 3, NOW(6))");
        app.update("INSERT INTO auth_history.revisions (revised_at) VALUES (NOW(6))");

        assertThat(app.update("DELETE FROM auth_sessions.login_throttles")).isEqualTo(1);
        assertDenied(() -> app.update("DELETE FROM users"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE auth_history.revisions SET revised_at = NOW(6)",
            "DELETE FROM auth_history.revisions",
            "DROP TABLE users",
            "ALTER TABLE users DROP CHECK chk_users_password_hash",
            "CREATE TABLE auth_sessions.shadow (id INT)",
            "CREATE TRIGGER tr_bypass BEFORE UPDATE ON users FOR EACH ROW SET NEW.role = 'SUPER_ADMIN'"
    })
    void applicationUserCannotRewriteHistoryOrTheSchema(String statement) {
        assertDenied(() -> app.execute(statement));
    }

    @Test
    void theSchemaRejectsUsersThatBreakTheDomainRules() {
        assertRejected("chk_users_email", "Mayusculas@clinica.test", "NURSE", "ACTIVE", null, null, null, HASH, "CURRENT", true);
        assertRejected("chk_users_role", "rol@clinica.test", "JANITOR", "ACTIVE", null, null, null, HASH, "CURRENT", true);
        assertRejected("chk_users_password_hash", "bcrypt@clinica.test", "NURSE", "ACTIVE", null, null, null,
                "$2a$10$abcdefghijklmnopqrstuv", "CURRENT", true);
        assertRejected("chk_users_status_details", "suspendida@clinica.test", "NURSE", "SUSPENDED", null, null, null, HASH, "CURRENT", true);
        assertRejected("chk_users_status_changed_by_role", "cambio@clinica.test", "NURSE", "SUSPENDED", "Suspensión preventiva",
                UUID.randomUUID().toString(), "DOCTOR", HASH, "CURRENT", true);
        assertRejected("chk_users_pending_without_credential", "pendiente@clinica.test", "NURSE", "PENDING_ACTIVATION", null, null, null,
                HASH, "CURRENT", true);
        assertRejected("chk_users_active_with_credential", "sinclave@clinica.test", "NURSE", "ACTIVE", null, null, null, null, "NOT_SET",
                false);
    }

    private static void assertRejected(String constraint, String email, String role, String status, String reason, String changedBy,
                                       String changedByRole, String hash, String credentialState, boolean passwordChanged) {
        assertThatThrownBy(() -> app.update(INSERT_USER, UUID.randomUUID().toString(), email, role, status, reason, changedBy, changedByRole,
                hash, credentialState, passwordChanged ? Timestamp.valueOf("2026-09-14 10:00:00") : null))
                .extracting(failure -> NestedExceptionUtils.getMostSpecificCause(failure).getMessage())
                .asString()
                .contains(constraint);
    }

    private static void assertDenied(ThrowingCallable statement) {
        assertThatThrownBy(statement)
                .extracting(failure -> NestedExceptionUtils.getMostSpecificCause(failure).getMessage())
                .asString()
                .containsAnyOf("command denied", "Access denied", "SUPER privilege");
    }
}
