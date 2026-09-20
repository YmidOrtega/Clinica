package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionsException;
import com.ClinicaDeYmid.admissions_service.domain.Authorization;
import com.ClinicaDeYmid.admissions_service.domain.AuthorizationStatus;
import com.ClinicaDeYmid.admissions_service.domain.AuthorizationType;
import com.ClinicaDeYmid.admissions_service.domain.Cause;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.ServiceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-20T10:15:30Z"), ZoneOffset.UTC);
    private final UUID item = UUID.randomUUID();

    @Test
    void anAuthorizationWithoutItemsCoversEverything() {
        Authorization authorization = grant(null, null, null);

        assertThat(authorization.authorizedItems()).isEmpty();
        assertThat(authorization.covers(UUID.randomUUID(), LocalDate.of(2026, 9, 20))).isTrue();
    }

    @Test
    void anAuthorizationWithItemsCoversOnlyThose() {
        Authorization authorization = grant(Set.of(item), null, null);

        assertThat(authorization.covers(item, LocalDate.of(2026, 9, 20))).isTrue();
        assertThat(authorization.covers(UUID.randomUUID(), LocalDate.of(2026, 9, 20))).isFalse();
    }

    @Test
    void anAuthorizationOnlyCoversInsideItsValidity() {
        Authorization authorization = grant(null, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 15));

        assertThat(authorization.covers(item, LocalDate.of(2026, 9, 12))).isTrue();
        assertThat(authorization.covers(item, LocalDate.of(2026, 9, 10))).isTrue();
        assertThat(authorization.covers(item, LocalDate.of(2026, 9, 15))).isTrue();
        assertThat(authorization.covers(item, LocalDate.of(2026, 9, 9))).isFalse();
        assertThat(authorization.covers(item, LocalDate.of(2026, 9, 16))).isFalse();
    }

    @Test
    void anOpenEndedAuthorizationHasNoUpperBound() {
        Authorization authorization = grant(null, LocalDate.of(2026, 1, 1), null);

        assertThat(authorization.covers(item, LocalDate.of(2030, 12, 31))).isTrue();
    }

    @Test
    void validityCannotEndBeforeItStarts() {
        assertThatThrownBy(() -> grant(null, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 10)))
                .isInstanceOf(AdmissionsException.InvalidData.class);
    }

    @Test
    void theCopaymentIsRoundedAndNeverNegative() {
        assertThat(grantWithCopayment(new BigDecimal("15000.456")).copayment())
                .isEqualByComparingTo(new BigDecimal("15000.46"));
        assertThatThrownBy(() -> grantWithCopayment(new BigDecimal("-1")))
                .isInstanceOf(AdmissionsException.InvalidData.class);
    }

    @Test
    void revokingKeepsTheReasonAndStopsCovering() {
        Authorization authorization = grant(null, null, null);

        authorization.revoke("El pagador la anuló por duplicada", clock);

        assertThat(authorization.status()).isEqualTo(new AuthorizationStatus.Revoked(
                "El pagador la anuló por duplicada", Instant.parse("2026-09-20T10:15:30Z")));
        assertThat(authorization.covers(item, LocalDate.of(2026, 9, 20))).isFalse();
    }

    @Test
    void anAuthorizationCannotBeRevokedTwice() {
        Authorization authorization = grant(null, null, null);
        authorization.revoke("Duplicada", clock);

        assertThatThrownBy(() -> authorization.revoke("Otra vez", clock))
                .isInstanceOf(AdmissionsException.AuthorizationAlreadyRevoked.class);
    }

    @Test
    void aClosedEpisodeAcceptsNoMoreAuthorizations() {
        Admission admission = admission();
        admission.cancel("Se registró dos veces", clock);

        assertThatThrownBy(() -> Authorization.grant(admission, "AUT-1", AuthorizationType.HOSPITALIZATION,
                null, null, null, null, null)).isInstanceOf(AdmissionsException.ClosedAdmission.class);
    }

    private Authorization grant(Set<UUID> items, LocalDate from, LocalDate to) {
        return Authorization.grant(admission(), "AUT-2026-001", AuthorizationType.HOSPITALIZATION,
                "Auditor del pagador", null, from, to, items);
    }

    private Authorization grantWithCopayment(BigDecimal copayment) {
        return Authorization.grant(admission(), "AUT-2026-002", AuthorizationType.AMBULATORY_SERVICES,
                null, copayment, null, null, null);
    }

    private Admission admission() {
        ConfigurationService service = ConfigurationService.configure(
                ServiceType.define("Hospitalización", AdmissionKind.INPATIENT), Location.define("Piso 3"));
        return Admission.register("ADM-2026-000001", UUID.randomUUID(), service, Cause.ILLNESS, null, null, clock);
    }
}
