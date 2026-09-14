package com.ClinicaDeYmid.auth_service.domain.throttle;

import com.ClinicaDeYmid.auth_service.domain.user.EmailAddress;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LoginThrottlePolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-14T15:00:00Z");
    private static final LoginThrottlePolicy POLICY = LoginThrottlePolicy.DEFAULT;

    @Test
    void theFirstFailuresFromAnAddressAreFree() {
        assertThat(POLICY.decide(FailureCount.NONE, failures(4, NOW), NOW)).isEqualTo(new LoginAttemptDecision.Allowed());
    }

    @Test
    void failuresFromTheSameAddressDoubleTheWaitUpToFifteenMinutes() {
        assertThat(POLICY.decide(FailureCount.NONE, failures(5, NOW), NOW))
                .isEqualTo(new LoginAttemptDecision.Delayed(Duration.ofSeconds(1)));
        assertThat(POLICY.decide(FailureCount.NONE, failures(9, NOW), NOW))
                .isEqualTo(new LoginAttemptDecision.Delayed(Duration.ofSeconds(16)));
        assertThat(POLICY.decide(FailureCount.NONE, failures(60, NOW), NOW))
                .isEqualTo(new LoginAttemptDecision.Delayed(Duration.ofMinutes(15)));
        assertThat(POLICY.decide(FailureCount.NONE, failures(9, NOW.minusSeconds(10)), NOW))
                .isEqualTo(new LoginAttemptDecision.Delayed(Duration.ofSeconds(6)));
        assertThat(POLICY.decide(FailureCount.NONE, failures(9, NOW.minusSeconds(20)), NOW)).isEqualTo(new LoginAttemptDecision.Allowed());
    }

    @Test
    void failuresAgainstAnAccountFromManyAddressesOnlySlowItDownForAMinute() {
        assertThat(POLICY.decide(failures(12, NOW), FailureCount.NONE, NOW))
                .isEqualTo(new LoginAttemptDecision.Delayed(Duration.ofSeconds(4)));
        assertThat(POLICY.decide(failures(99, NOW), failures(1, NOW), NOW))
                .isEqualTo(new LoginAttemptDecision.Delayed(Duration.ofMinutes(1)));
    }

    @Test
    void anAccountLocksAfterOneHundredConsecutiveFailures() {
        assertThat(POLICY.decide(failures(100, NOW.minus(Duration.ofDays(3))), FailureCount.NONE, NOW))
                .isEqualTo(new LoginAttemptDecision.Locked());
    }

    @Test
    void keysDoNotDependOnLetterCaseOrSurroundingSpaces() {
        EmailAddress email = new EmailAddress("Ana@Clinica.test");

        assertThat(new ThrottleKey.AccountFromAddress(email, " 2001:DB8::1 ").identity())
                .isEqualTo(new ThrottleKey.AccountFromAddress(new EmailAddress("ana@clinica.test"), "2001:db8::1").identity());
        assertThat(new ThrottleKey.Account(email).identity()).isNotEqualTo(new ThrottleKey.AccountFromAddress(email, "2001:db8::1").identity());
    }

    private static FailureCount failures(int count, Instant lastFailureAt) {
        return new FailureCount(count, lastFailureAt);
    }
}
