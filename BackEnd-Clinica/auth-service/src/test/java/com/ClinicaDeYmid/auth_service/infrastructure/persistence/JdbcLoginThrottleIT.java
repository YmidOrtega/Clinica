package com.ClinicaDeYmid.auth_service.infrastructure.persistence;

import com.ClinicaDeYmid.auth_service.domain.throttle.FailureCount;
import com.ClinicaDeYmid.auth_service.domain.throttle.ThrottleKey;
import com.ClinicaDeYmid.auth_service.domain.user.EmailAddress;
import com.ClinicaDeYmid.auth_service.support.MySqlTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JdbcLoginThrottle.class, MySqlTestContainer.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JdbcLoginThrottleIT {

    private static final Instant NOW = Instant.parse("2026-09-14T15:00:00.123456Z");
    private static final EmailAddress ANA = new EmailAddress("ana@clinica.test");

    @Autowired
    private JdbcLoginThrottle throttle;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM auth_sessions.login_throttles");
    }

    @Test
    void countsConsecutiveFailuresPerKeyUntilCleared() {
        ThrottleKey account = new ThrottleKey.Account(ANA);
        ThrottleKey fromAddress = new ThrottleKey.AccountFromAddress(ANA, "203.0.113.7");

        throttle.recordFailure(account, NOW);
        FailureCount second = throttle.recordFailure(account, NOW.plusSeconds(5));
        throttle.recordFailure(fromAddress, NOW);

        assertThat(second).isEqualTo(new FailureCount(2, NOW.plusSeconds(5)));
        assertThat(throttle.failuresOf(fromAddress)).isEqualTo(new FailureCount(1, NOW));

        throttle.clear(account);

        assertThat(throttle.failuresOf(account)).isEqualTo(FailureCount.NONE);
        assertThat(throttle.failuresOf(fromAddress).consecutiveFailures()).isEqualTo(1);
    }

    @Test
    void neverStoresTheEmailOrTheAddress() {
        throttle.recordFailure(new ThrottleKey.AccountFromAddress(ANA, "203.0.113.7"), NOW);

        String stored = jdbc.queryForObject("SELECT key_hash FROM auth_sessions.login_throttles", String.class);

        assertThat(stored).hasSize(64).doesNotContain("ana").doesNotContain("203");
    }

    @Test
    void concurrentFailuresAreAllCounted() throws Exception {
        ThrottleKey account = new ThrottleKey.Account(ANA);
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            IntStream.range(0, 40).forEach(attempt -> executor.submit(() -> throttle.recordFailure(account, NOW)));
        }

        assertThat(throttle.failuresOf(account).consecutiveFailures()).isEqualTo(40);
    }

    @Test
    void purgesStaleCountersButKeepsLockedAccounts() {
        LoginThrottleProperties properties = new LoginThrottleProperties(5, Duration.ofMinutes(15), 10, Duration.ofMinutes(1), 100,
                Duration.ofDays(30), 1);
        Instant old = NOW.minus(Duration.ofDays(31));
        ThrottleKey stale = new ThrottleKey.Account(new EmailAddress("viejo@clinica.test"));
        ThrottleKey locked = new ThrottleKey.Account(new EmailAddress("bloqueado@clinica.test"));
        ThrottleKey recent = new ThrottleKey.Account(new EmailAddress("reciente@clinica.test"));
        throttle.recordFailure(stale, old);
        jdbc.update("INSERT INTO auth_sessions.login_throttles VALUES (?, 100, ?)", JdbcLoginThrottle.hash(locked), Timestamp.from(old));
        throttle.recordFailure(recent, NOW.minus(Duration.ofDays(1)));

        int purged = new LoginThrottlePurger(jdbc, properties, Clock.fixed(NOW.truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC)).purgeStale();

        assertThat(purged).isEqualTo(1);
        assertThat(throttle.failuresOf(stale)).isEqualTo(FailureCount.NONE);
        assertThat(throttle.failuresOf(locked).consecutiveFailures()).isEqualTo(100);
        assertThat(throttle.failuresOf(recent).consecutiveFailures()).isEqualTo(1);
    }
}
