package com.ClinicaDeYmid.auth_service.infrastructure.persistence;

import com.ClinicaDeYmid.auth_service.domain.user.EmailAddress;
import com.ClinicaDeYmid.auth_service.domain.user.Role;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.UserException;
import com.ClinicaDeYmid.auth_service.domain.user.UserStatus;
import com.ClinicaDeYmid.auth_service.infrastructure.config.ClockConfiguration;
import com.ClinicaDeYmid.auth_service.infrastructure.config.PersistenceConfiguration;
import com.ClinicaDeYmid.auth_service.support.MySqlTestContainer;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.envers.AuditReaderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static com.ClinicaDeYmid.auth_service.domain.user.UserFixtures.ADMIN;
import static com.ClinicaDeYmid.auth_service.domain.user.UserFixtures.CLOCK;
import static com.ClinicaDeYmid.auth_service.domain.user.UserFixtures.HASH;
import static com.ClinicaDeYmid.auth_service.domain.user.UserFixtures.SUPER_ADMIN;
import static com.ClinicaDeYmid.auth_service.domain.user.UserFixtures.active;
import static com.ClinicaDeYmid.auth_service.domain.user.UserFixtures.invited;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaUsers.class, PersistenceConfiguration.class, ClockConfiguration.class, MySqlTestContainer.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserPersistenceIT {

    @Autowired
    private JpaUsers users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void clean() {
        jdbc.update("SET FOREIGN_KEY_CHECKS = 0");
        jdbc.update("DELETE FROM auth_history.users_aud");
        jdbc.update("DELETE FROM auth_history.revisions");
        jdbc.update("DELETE FROM users");
        jdbc.update("SET FOREIGN_KEY_CHECKS = 1");
    }

    @Test
    void storesAndRebuildsEveryStatusAndCredentialState() {
        User saved = users.save(active(Role.DOCTOR));
        User found = users.findByEmail(new EmailAddress("ANA.ROJAS@clinica.test")).orElseThrow();

        found.suspend("Investigación por acceso indebido", ADMIN, CLOCK);
        users.save(found);
        User suspended = users.findByUuid(saved.uuid()).orElseThrow();

        assertThat(suspended.status()).isEqualTo(new UserStatus.Suspended("Investigación por acceso indebido", ADMIN, suspended.statusChangedAt()));
        assertThat(suspended.currentHash()).contains(HASH);
        assertThat(suspended.fullName().value()).isEqualTo("Ana María Rojas");
        assertThat(suspended.createdAt()).isNotNull();
        assertThat(users.existsByEmail(new EmailAddress("ana.rojas@clinica.test"))).isTrue();
    }

    @Test
    void anEmailBelongsToASingleUser() {
        users.save(invited(Role.NURSE, "compartido@clinica.test"));

        assertThatThrownBy(() -> users.save(invited(Role.DOCTOR, "compartido@clinica.test")))
                .isInstanceOf(UserException.EmailAlreadyRegistered.class);
    }

    @Test
    void concurrentAdministratorsCannotOverwriteEachOther() {
        User saved = users.save(active(Role.NURSE));
        User first = users.findByUuid(saved.uuid()).orElseThrow();
        User second = users.findByUuid(saved.uuid()).orElseThrow();

        first.suspend("Suspensión del primer administrador", ADMIN, CLOCK);
        users.save(first);
        second.deactivate("Retiro del segundo administrador", ADMIN, CLOCK);

        assertThatThrownBy(() -> users.save(second)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void countsActiveUsersByRole() {
        users.save(active(Role.SUPER_ADMIN, "uno@clinica.test"));
        users.save(active(Role.SUPER_ADMIN, "dos@clinica.test"));
        users.save(invited(Role.SUPER_ADMIN, "tres@clinica.test"));
        User suspended = active(Role.SUPER_ADMIN, "cuatro@clinica.test");
        suspended.suspend("Suspensión preventiva del cuarto", SUPER_ADMIN, CLOCK);
        users.save(suspended);

        assertThat(users.countActiveWithRole(Role.SUPER_ADMIN)).isEqualTo(2);
        assertThat(users.countActiveWithRole(Role.ADMIN)).isZero();
    }

    @Test
    void keepsTheHistoryOfEveryChangeWithoutPasswordHashes() {
        User saved = users.save(invited(Role.NURSE));
        User pending = users.findByUuid(saved.uuid()).orElseThrow();
        pending.activate(HASH, CLOCK);
        users.save(pending);
        User activeUser = users.findByUuid(saved.uuid()).orElseThrow();
        activeUser.changeRole(Role.DOCTOR, ADMIN, CLOCK);
        users.save(activeUser);

        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT role, status, credential_state FROM auth_history.users_aud ORDER BY rev");
        List<String> historyColumns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'auth_history' AND table_name = 'users_aud'""", String.class);
        List<Number> revisions = transactions.execute(status -> AuditReaderFactory
                .get(entityManagerFactory.createEntityManager()).getRevisions(User.class, idOf(saved)));

        assertThat(history).extracting(row -> row.get("role") + "/" + row.get("status") + "/" + row.get("credential_state"))
                .containsExactly("NURSE/PENDING_ACTIVATION/NOT_SET", "NURSE/ACTIVE/CURRENT", "DOCTOR/ACTIVE/CURRENT");
        assertThat(historyColumns).map(String::toLowerCase).doesNotContain("password_hash");
        assertThat(revisions).hasSize(3);
    }

    private Long idOf(User user) {
        return jdbc.queryForObject("SELECT id FROM users WHERE uuid = ?", Long.class, user.uuid().toString());
    }
}
