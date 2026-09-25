package com.ClinicaDeYmid.billing_service.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EpisodeAccountTest {

    private static final UUID ADMISSION = UUID.fromString("5b1f7c9e-2d3a-4e5f-8a9b-0c1d2e3f4a5b");
    private static final Instant REGISTERED_AT = Instant.parse("2026-09-25T13:00:00Z");
    private static final Instant DISCHARGED_AT = Instant.parse("2026-09-27T18:30:00Z");

    @Test
    void opensWhenTheEpisodeIsRegistered() {
        EpisodeAccount account = EpisodeAccount.open(snapshot(0, AdmissionSnapshot.Status.REGISTERED, REGISTERED_AT));

        assertThat(account.status()).isInstanceOf(AccountStatus.Open.class);
        assertThat(account.status().acceptsCharges()).isTrue();
        assertThat(account.openedAt()).isEqualTo(REGISTERED_AT);
        assertThat(account.admissionNumber()).isEqualTo("ADM-2026-000123");
    }

    @Test
    void freezesWhenThePatientIsDischarged() {
        EpisodeAccount account = EpisodeAccount.open(snapshot(0, AdmissionSnapshot.Status.REGISTERED, REGISTERED_AT));

        assertThat(account.follow(discharged(3))).isTrue();

        assertThat(account.status()).isEqualTo(new AccountStatus.Frozen(DischargeType.MEDICAL, DISCHARGED_AT));
        assertThat(account.status().acceptsCharges()).isTrue();
        assertThat(account.admissionVersion()).isEqualTo(3);
    }

    @Test
    void isVoidedWhenTheAdmissionIsCancelled() {
        EpisodeAccount account = EpisodeAccount.open(snapshot(0, AdmissionSnapshot.Status.REGISTERED, REGISTERED_AT));

        account.follow(new AdmissionSnapshot(ADMISSION, "ADM-2026-000123", 1, UUID.randomUUID(),
                AdmissionKind.EMERGENCY, AdmissionSnapshot.Status.CANCELLED, UUID.randomUUID(), DISCHARGED_AT, null,
                "Paciente registrado por error"));

        assertThat(account.status()).isEqualTo(new AccountStatus.Voided("Paciente registrado por error", DISCHARGED_AT));
        assertThat(account.status().acceptsCharges()).isFalse();
    }

    @Test
    void ignoresAnOlderVersionOfTheEpisode() {
        EpisodeAccount account = EpisodeAccount.open(snapshot(4, AdmissionSnapshot.Status.ACTIVE, REGISTERED_AT));

        assertThat(account.follow(snapshot(2, AdmissionSnapshot.Status.REGISTERED, REGISTERED_AT))).isFalse();

        assertThat(account.admissionStatus()).isEqualTo(AdmissionSnapshot.Status.ACTIVE);
        assertThat(account.admissionVersion()).isEqualTo(4);
    }

    @Test
    void appliesEveryEventOfTheSameVersion() {
        EpisodeAccount account = EpisodeAccount.open(snapshot(0, AdmissionSnapshot.Status.REGISTERED, REGISTERED_AT));

        assertThat(account.follow(snapshot(0, AdmissionSnapshot.Status.REGISTERED, REGISTERED_AT))).isTrue();
    }

    @Test
    void aClosedAccountNoLongerFollowsTheEpisode() {
        EpisodeAccount account = EpisodeAccount.open(discharged(5));

        assertThat(account.status()).isInstanceOf(AccountStatus.Frozen.class);
        assertThat(account.follow(snapshot(6, AdmissionSnapshot.Status.ACTIVE, REGISTERED_AT))).isFalse();
        assertThat(account.status()).isInstanceOf(AccountStatus.Frozen.class);
    }

    @Test
    void refusesAnEventOfAnotherEpisode() {
        EpisodeAccount account = EpisodeAccount.open(snapshot(0, AdmissionSnapshot.Status.REGISTERED, REGISTERED_AT));

        assertThatThrownBy(() -> account.follow(new AdmissionSnapshot(UUID.randomUUID(), "ADM-2026-000124", 1,
                UUID.randomUUID(), AdmissionKind.EMERGENCY, AdmissionSnapshot.Status.ACTIVE, UUID.randomUUID(),
                REGISTERED_AT, null, null))).isInstanceOf(IllegalArgumentException.class);
    }

    private static AdmissionSnapshot discharged(long version) {
        return new AdmissionSnapshot(ADMISSION, "ADM-2026-000123", version, UUID.randomUUID(), AdmissionKind.INPATIENT,
                AdmissionSnapshot.Status.DISCHARGED, UUID.randomUUID(), DISCHARGED_AT, DischargeType.MEDICAL, null);
    }

    private static AdmissionSnapshot snapshot(long version, AdmissionSnapshot.Status status, Instant at) {
        return new AdmissionSnapshot(ADMISSION, "ADM-2026-000123", version, UUID.randomUUID(),
                AdmissionKind.EMERGENCY, status, UUID.randomUUID(), at, null, null);
    }
}
