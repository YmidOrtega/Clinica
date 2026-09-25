package com.ClinicaDeYmid.billing_service.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NumberingResolutionTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Clock TODAY = Clock.fixed(Instant.parse("2026-09-25T15:00:00Z"), BOGOTA);
    private static final String KEY = "fc8eac422eba16e22ffd8c6f94b3f40a6e38162c";

    @Test
    void startsPendingAndIssuesNumbersOnlyOnceActive() {
        NumberingResolution resolution = resolution(1, 100, "2026-01-01", "2027-12-31");

        assertThat(resolution.status()).isInstanceOf(ResolutionStatus.Pending.class);
        assertThatThrownBy(() -> resolution.issue(1, TODAY)).isInstanceOf(BillingException.NoActiveResolution.class);

        resolution.activate(DianEnvironment.TEST, TODAY);

        IssuedNumber issued = resolution.issue(1, TODAY);
        assertThat(issued.formatted()).isEqualTo("FE1");
        assertThat(resolution.status()).isInstanceOf(ResolutionStatus.Active.class);
    }

    @Test
    void theLastNumberOfTheRangeExhaustsTheResolution() {
        NumberingResolution resolution = active(1, 3);

        resolution.issue(3, TODAY);

        assertThat(resolution.status()).isInstanceOf(ResolutionStatus.Exhausted.class);
        assertThatThrownBy(() -> resolution.issue(4, TODAY)).isInstanceOf(BillingException.NoActiveResolution.class);
    }

    @Test
    void neverIssuesANumberOutsideTheRange() {
        NumberingResolution resolution = active(10, 20);

        assertThatThrownBy(() -> resolution.issue(21, TODAY)).isInstanceOf(BillingException.ResolutionExhausted.class);
        assertThatThrownBy(() -> resolution.issue(9, TODAY)).isInstanceOf(BillingException.ResolutionExhausted.class);
    }

    @Test
    void cannotBeActivatedOutsideItsValidityNorInAnotherEnvironment() {
        assertThatThrownBy(() -> resolution(1, 100, "2027-01-01", "2027-12-31").activate(DianEnvironment.TEST, TODAY))
                .isInstanceOf(BillingException.OutsideValidity.class);
        assertThatThrownBy(() -> resolution(1, 100, "2025-12-15", "2026-09-24").activate(DianEnvironment.TEST, TODAY))
                .isInstanceOf(BillingException.OutsideValidity.class);
        assertThatThrownBy(() -> resolution(1, 100, "2026-01-01", "2027-12-31").activate(DianEnvironment.PRODUCTION, TODAY))
                .isInstanceOf(BillingException.EnvironmentMismatch.class);
    }

    @Test
    void anExpiredResolutionStopsNumbering() {
        NumberingResolution resolution = active(1, 100);
        Clock afterExpiry = Clock.fixed(Instant.parse("2028-01-02T15:00:00Z"), BOGOTA);

        assertThatThrownBy(() -> resolution.issue(1, afterExpiry)).isInstanceOf(BillingException.OutsideValidity.class);
        assertThat(resolution.alerts(1, afterExpiry)).containsExactly(ResolutionAlert.EXPIRED);
    }

    @Test
    void warnsBeforeTheRangeOrTheValidityRunOut() {
        NumberingResolution resolution = active(1, 100);

        assertThat(resolution.alerts(90, TODAY)).isEmpty();
        assertThat(resolution.alerts(91, TODAY)).containsExactly(ResolutionAlert.RUNNING_OUT);
        assertThat(resolution.remaining(91)).isEqualTo(10);

        NumberingResolution closeToExpiry = resolution(1, 100, "2026-01-01", "2026-10-20");
        assertThat(closeToExpiry.alerts(1, TODAY)).containsExactly(ResolutionAlert.EXPIRES_SOON);
    }

    @Test
    void retiresFromPendingOrActiveButNotAfterwards() {
        NumberingResolution resolution = active(1, 1);
        resolution.issue(1, TODAY);

        assertThatThrownBy(() -> resolution.retire("Error de digitación", TODAY))
                .isInstanceOf(BillingException.InvalidResolutionTransition.class);

        NumberingResolution pending = resolution(1, 100, "2026-01-01", "2027-12-31");
        pending.retire("Error de digitación", TODAY);
        assertThat(pending.status()).isEqualTo(new ResolutionStatus.Retired("Error de digitación", TODAY.instant()));
    }

    @Test
    void termsRefuseImpossibleRangesAndDates() {
        assertThatThrownBy(() -> terms(10, 9, "2026-01-01", "2027-12-31")).isInstanceOf(BillingException.InvalidData.class);
        assertThatThrownBy(() -> terms(0, 9, "2026-01-01", "2027-12-31")).isInstanceOf(BillingException.InvalidData.class);
        assertThatThrownBy(() -> terms(1, 9, "2026-01-01", "2025-12-31")).isInstanceOf(BillingException.InvalidData.class);
        assertThatThrownBy(() -> new ResolutionTerms("18760000001", LocalDate.parse("2026-01-01"), "FACTU", 1, 9,
                LocalDate.parse("2026-01-01"), LocalDate.parse("2027-01-01"), KEY))
                .isInstanceOf(BillingException.InvalidData.class);
    }

    @Test
    void rangesOverlapOnlyWithinTheSamePrefix() {
        ResolutionTerms first = terms(1, 100, "2026-01-01", "2027-12-31");

        assertThat(first.overlaps(terms(100, 200, "2026-01-01", "2027-12-31"))).isTrue();
        assertThat(first.overlaps(terms(101, 200, "2026-01-01", "2027-12-31"))).isFalse();
        assertThat(first.overlaps(new ResolutionTerms("18760000002", LocalDate.parse("2026-01-01"), "FV", 1, 100,
                LocalDate.parse("2026-01-01"), LocalDate.parse("2027-12-31"), KEY))).isFalse();
    }

    private static NumberingResolution active(long from, long to) {
        NumberingResolution resolution = resolution(from, to, "2026-01-01", "2027-12-31");
        resolution.activate(DianEnvironment.TEST, TODAY);
        return resolution;
    }

    private static NumberingResolution resolution(long from, long to, String validFrom, String validUntil) {
        return NumberingResolution.register(terms(from, to, validFrom, validUntil), DianEnvironment.TEST);
    }

    private static ResolutionTerms terms(long from, long to, String validFrom, String validUntil) {
        return new ResolutionTerms("18760000001", LocalDate.parse("2025-12-15"), "fe", from, to,
                LocalDate.parse(validFrom), LocalDate.parse(validUntil), KEY.toUpperCase());
    }
}
