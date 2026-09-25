package com.ClinicaDeYmid.billing_service.infrastructure.messaging;

import com.ClinicaDeYmid.billing_service.domain.AdmissionKind;
import com.ClinicaDeYmid.billing_service.domain.AdmissionSnapshot;
import com.ClinicaDeYmid.billing_service.domain.DischargeType;
import com.ClinicaDeYmid.billing_service.support.AdmissionEvents;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdmissionEventMapperTest {

    private final UUID admission = UUID.randomUUID();

    @Test
    void readsTheEpisodeSummaryCarriedByEveryEvent() {
        AdmissionSnapshot snapshot = AdmissionEventMapper.toSnapshot(
                AdmissionEvents.discharged(admission, "ADM-2026-000321", 4, "REFERRAL")).orElseThrow();

        assertThat(snapshot.admissionUuid()).isEqualTo(admission);
        assertThat(snapshot.admissionNumber()).isEqualTo("ADM-2026-000321");
        assertThat(snapshot.admissionVersion()).isEqualTo(4);
        assertThat(snapshot.kind()).isEqualTo(AdmissionKind.INPATIENT);
        assertThat(snapshot.status()).isEqualTo(AdmissionSnapshot.Status.DISCHARGED);
        assertThat(snapshot.discharge()).isEqualTo(DischargeType.REFERRAL);
        assertThat(snapshot.occurredAt()).isEqualTo(Instant.parse("2026-09-25T15:00:00Z"));
    }

    @Test
    void keepsTheReasonOnlyWhenTheAdmissionIsCancelled() {
        assertThat(AdmissionEventMapper.toSnapshot(
                AdmissionEvents.cancelled(admission, "ADM-2026-000321", 1, "Duplicado")).orElseThrow().reason())
                .isEqualTo("Duplicado");
        assertThat(AdmissionEventMapper.toSnapshot(
                AdmissionEvents.phaseChanged(admission, "ADM-2026-000321", 1, "EMERGENCY")).orElseThrow().reason())
                .isNull();
    }

    @Test
    void ignoresEventTypesItDoesNotKnowYet() {
        assertThat(AdmissionEventMapper.toSnapshot(
                AdmissionEvents.registered(admission, "ADM-2026-000321", 0)
                        .replace("AdmissionRegistered", "AdmissionTransferredAbroad"))).isEmpty();
    }

    @Test
    void refusesAnEventThatBreaksTheContract() {
        assertThatThrownBy(() -> AdmissionEventMapper.toSnapshot("{\"type\":\"AdmissionRegistered\"}"))
                .isInstanceOf(MalformedAdmissionEventException.class);
        assertThatThrownBy(() -> AdmissionEventMapper.toSnapshot("not json"))
                .isInstanceOf(MalformedAdmissionEventException.class);
        assertThatThrownBy(() -> AdmissionEventMapper.toSnapshot(
                AdmissionEvents.registered(admission, "ADM-26-1", 0)))
                .isInstanceOf(MalformedAdmissionEventException.class);
    }
}
