package com.ClinicaDeYmid.clinical_history_service.infrastructure.messaging;

import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.clinical_history_service.support.PatientEvents;
import com.ClinicaDeYmid.clinical_history_service.support.ProducerContract;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientEventMapperTest {

    @Test
    void theFixturesFollowThePublishedProducerContract() {
        UUID uuid = UUID.randomUUID();
        UUID real = UUID.randomUUID();
        List<String> fixtures = List.of(
                PatientEvents.registered(uuid, 0),
                PatientEvents.registered(uuid, 4, "PatientDied", "DECEASED", "2026-09-01"),
                PatientEvents.unidentified(uuid, 0, "UnidentifiedPatientRegistered", "UNIDENTIFIED", null, null),
                PatientEvents.unidentified(uuid, 1, "UnidentifiedPatientIdentified", "IDENTIFIED", real, null),
                PatientEvents.unidentified(uuid, 2, "UnidentifiedPatientIdentificationReverted", "UNIDENTIFIED", null, real));

        assertThat(fixtures).allSatisfy(json -> assertThat(ProducerContract.violations(json)).isEmpty());
    }

    @Test
    void mapsRegisteredPatientsWithTheirVersionAndStatus() {
        UUID uuid = UUID.randomUUID();

        PatientReference reference = PatientEventMapper.toReference(
                PatientEvents.registered(uuid, 4, "PatientDied", "DECEASED", "2026-09-01")).orElseThrow();

        assertThat(reference).isInstanceOfSatisfying(PatientReference.Registered.class, registered -> {
            assertThat(registered.uuid()).isEqualTo(uuid);
            assertThat(registered.version()).isEqualTo(4);
            assertThat(registered.status()).isEqualTo(PatientReference.Registered.Status.DECEASED);
            assertThat(registered.dateOfDeath()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(registered.document().number()).isEqualTo("1098765432");
            assertThat(registered.healthProviderNit()).isEqualTo("900123456-7");
        });
    }

    @Test
    void mapsTheIdentificationLinkOfUnidentifiedPatients() {
        UUID uuid = UUID.randomUUID();
        UUID real = UUID.randomUUID();

        PatientReference reference = PatientEventMapper.toReference(
                PatientEvents.unidentified(uuid, 1, "UnidentifiedPatientIdentified", "IDENTIFIED", real, null)).orElseThrow();

        assertThat(reference).isInstanceOfSatisfying(PatientReference.Unidentified.class,
                unidentified -> assertThat(unidentified.identifiedAs()).contains(real));
    }

    @Test
    void appliesFuturePatientEventTypesBecauseTheyAlwaysCarryTheSnapshot() {
        UUID uuid = UUID.randomUUID();

        assertThat(PatientEventMapper.toReference(PatientEvents.registered(uuid, 7, "PatientNicknameChanged", "ACTIVE", null)))
                .hasValueSatisfying(reference -> assertThat(reference.version()).isEqualTo(7));
    }

    @Test
    void ignoresEventFamiliesItDoesNotKnow() {
        assertThat(PatientEventMapper.toReference("""
                {"type": "HouseholdMemberAdded", "patientVersion": 9, "data": {}}
                """)).isEmpty();
    }

    @Test
    void rejectsEventsThatBreakTheContract() {
        assertThatThrownBy(() -> PatientEventMapper.toReference("{not json"))
                .isInstanceOf(MalformedPatientEventException.class);
        assertThatThrownBy(() -> PatientEventMapper.toReference("""
                {"type": "PatientRegistered", "patientVersion": 0, "data": {"patient": {"uuid": "x"}}}
                """)).isInstanceOf(MalformedPatientEventException.class);
    }
}
