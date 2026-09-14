package com.ClinicaDeYmid.clinical_history_service.domain.access;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.clockAt;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.doctor;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.nurse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-14T15:00:00Z");
    private static final UUID PATIENT = UUID.randomUUID();

    private final AccessPolicy policy = new AccessPolicy(Duration.ofDays(30), Duration.ofHours(4));
    private final Clinician doctor = doctor();

    @Test
    void theCareTeamOfAnOpenEncounterReachesTheWholeRecordOfThePatient() {
        CareTeamMembership open = membership(UUID.randomUUID(), doctor, null);

        assertThat(policy.toPatient(doctor, List.of(open), List.of(), NOW)).isEqualTo(new AccessDecision.Granted(AccessBasis.CARE_RELATIONSHIP));
        assertThat(policy.toPatient(nurse(), List.of(open), List.of(), NOW)).isEqualTo(new AccessDecision.Denied());
        assertThat(policy.toPatient(doctor, List.of(), List.of(), NOW)).isEqualTo(new AccessDecision.Denied());
    }

    @Test
    void theRelationshipEndsThirtyDaysAfterTheEncounterCloses() {
        CareTeamMembership recent = membership(UUID.randomUUID(), doctor, NOW.minus(Duration.ofDays(30)));
        CareTeamMembership old = membership(UUID.randomUUID(), doctor, NOW.minus(Duration.ofDays(30)).minusSeconds(1));

        assertThat(policy.toPatient(doctor, List.of(recent), List.of(), NOW)).isInstanceOf(AccessDecision.Granted.class);
        assertThat(policy.toPatient(doctor, List.of(old), List.of(), NOW)).isInstanceOf(AccessDecision.Denied.class);
    }

    @Test
    void writingNeedsMembershipInThatEncounter() {
        UUID encounter = UUID.randomUUID();
        CareTeamMembership elsewhere = membership(UUID.randomUUID(), doctor, null);

        assertThat(policy.toEncounter(doctor, encounter, List.of(elsewhere), List.of(), NOW)).isInstanceOf(AccessDecision.Denied.class);
        assertThat(policy.toEncounter(doctor, encounter, List.of(elsewhere, membership(encounter, doctor, null)), List.of(), NOW))
                .isInstanceOf(AccessDecision.Granted.class);
    }

    @Test
    void restrictedNotesStayWithTheirAuthorAndTheirEncounterTeam() {
        UUID encounter = UUID.randomUUID();
        Clinician author = doctor();
        CareTeamMembership otherEncounter = membership(UUID.randomUUID(), doctor, null);

        assertThat(policy.toRestrictedNote(author, author.uuid(), encounter, List.of(), List.of(), NOW))
                .isEqualTo(new AccessDecision.Granted(AccessBasis.AUTHOR));
        assertThat(policy.toRestrictedNote(doctor, author.uuid(), encounter, List.of(otherEncounter), List.of(), NOW))
                .isInstanceOf(AccessDecision.Denied.class);
        assertThat(policy.toRestrictedNote(doctor, author.uuid(), encounter, List.of(membership(encounter, doctor, null)), List.of(), NOW))
                .isEqualTo(new AccessDecision.Granted(AccessBasis.CARE_RELATIONSHIP));
    }

    @Test
    void emergencyAccessOpensEverythingForFourHoursToItsHolderOnly() {
        EmergencyAccess access = EmergencyAccess.grant(PATIENT, doctor, "Paciente inconsciente en reanimación", policy.emergencyAccessDuration(),
                clockAt(NOW));
        UUID encounter = UUID.randomUUID();

        assertThat(access.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(4)));
        assertThat(policy.toRestrictedNote(doctor, UUID.randomUUID(), encounter, List.of(), List.of(access), NOW.plus(Duration.ofHours(3))))
                .isEqualTo(new AccessDecision.Granted(AccessBasis.EMERGENCY_ACCESS));
        assertThat(policy.toPatient(doctor, List.of(), List.of(access), NOW.plus(Duration.ofHours(4)))).isInstanceOf(AccessDecision.Denied.class);
        assertThat(policy.toPatient(nurse(), List.of(), List.of(access), NOW)).isInstanceOf(AccessDecision.Denied.class);
    }

    @Test
    void emergencyAccessNeedsARealJustification() {
        assertThatThrownBy(() -> EmergencyAccess.grant(PATIENT, doctor, "urgente", Duration.ofHours(4), clockAt(NOW)))
                .isInstanceOf(ClinicalException.InvalidData.class);
        assertThatThrownBy(() -> EmergencyAccess.grant(PATIENT, doctor, "  ", Duration.ofHours(4), clockAt(NOW)))
                .isInstanceOf(ClinicalException.InvalidData.class);
    }

    private static CareTeamMembership membership(UUID encounterId, Clinician clinician, Instant closedAt) {
        return new CareTeamMembership(encounterId, PATIENT, clinician.uuid(), NOW.minus(Duration.ofDays(40)), closedAt);
    }
}
