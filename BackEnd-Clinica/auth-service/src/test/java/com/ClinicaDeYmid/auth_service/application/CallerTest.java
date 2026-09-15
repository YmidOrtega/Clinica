package com.ClinicaDeYmid.auth_service.application;

import com.ClinicaDeYmid.auth_service.domain.user.Actor;
import com.ClinicaDeYmid.auth_service.domain.user.Role;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallerTest {

    private static final Instant NOW = Instant.parse("2026-09-14T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Actor ADMIN = new Actor(UUID.randomUUID(), Role.ADMIN);

    @Test
    void aSecondFactorFromTheLastFiveMinutesIsRecentEnough() {
        assertThatCode(() -> new Caller(ADMIN, NOW.minusSeconds(300), true).requireRecentAuthentication(CLOCK)).doesNotThrowAnyException();
    }

    @Test
    void anOlderAuthenticationOrOneWithoutASecondFactorNeedsAStepUp() {
        assertThatThrownBy(() -> new Caller(ADMIN, NOW.minusSeconds(301), true).requireRecentAuthentication(CLOCK))
                .isInstanceOfSatisfying(StepUpRequired.class, required -> assertThat(required.maxAge()).isEqualTo(Caller.RECENT_AUTHENTICATION));
        assertThatThrownBy(() -> new Caller(ADMIN, NOW, false).requireRecentAuthentication(CLOCK)).isInstanceOf(StepUpRequired.class);
    }
}
