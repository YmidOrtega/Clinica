package com.ClinicaDeYmid.patient_service.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.ClinicaDeYmid.patient_service.domain.PatientFixtures.TODAY;
import static com.ClinicaDeYmid.patient_service.domain.PatientFixtures.registeredAdult;
import static com.ClinicaDeYmid.patient_service.domain.PatientFixtures.today;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnidentifiedPatientTest {

    static UnidentifiedPatient arrivedAtEmergencies() {
        return UnidentifiedPatient.register("NN-2026-000001", Sex.MALE, 1980,
                "Hombre adulto, cicatriz en antebrazo izquierdo, traído por ambulancia", today());
    }

    @Test
    void registersAsUnidentifiedAndRecordsTheEvent() {
        UnidentifiedPatient patient = arrivedAtEmergencies();

        assertThat(patient.uuid()).isNotNull();
        assertThat(patient.code()).isEqualTo("NN-2026-000001");
        assertThat(patient.status()).isEqualTo(new UnidentifiedPatientStatus.Unidentified());
        assertThat(patient.pullEvents()).containsExactly(new UnidentifiedPatientEvent.Registered());
    }

    @Test
    void formatsCodesWithYearAndSequence() {
        assertThat(UnidentifiedPatientCode.of(2026, 123)).isEqualTo("NN-2026-000123");
        assertThatThrownBy(() -> UnidentifiedPatientCode.of(2026, 1_000_000)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1899, 2027})
    void rejectsImplausibleEstimatedBirthYears(int year) {
        assertThatThrownBy(() -> UnidentifiedPatient.register("NN-2026-000001", Sex.FEMALE, year, "Mujer", today()))
                .isInstanceOf(PatientException.InvalidData.class)
                .hasMessageContaining("estimatedBirthYear");
    }

    @Test
    void requiresADescriptionToRecognizeThePerson() {
        assertThatThrownBy(() -> UnidentifiedPatient.register("NN-2026-000001", Sex.FEMALE, 1990, " ", today()))
                .isInstanceOf(PatientException.InvalidData.class);
    }

    @Test
    void identifiesAsAnActivePatientWithAReason() {
        UnidentifiedPatient unidentified = pulled(arrivedAtEmergencies());
        Patient patient = registeredAdult();

        unidentified.identifyAs(patient, "Familiar presentó la cédula", today());

        assertThat(unidentified.status()).isInstanceOfSatisfying(UnidentifiedPatientStatus.Identified.class, identified -> {
            assertThat(identified.patientUuid()).isEqualTo(patient.uuid());
            assertThat(identified.reason()).isEqualTo("Familiar presentó la cédula");
        });
        assertThat(unidentified.pullEvents()).containsExactly(new UnidentifiedPatientEvent.Identified(patient.uuid()));
    }

    @Test
    void refusesToIdentifyWithoutReasonOrAsAnInactivePatient() {
        UnidentifiedPatient unidentified = arrivedAtEmergencies();
        Patient inactive = registeredAdult();
        inactive.deactivate("Registro duplicado", today());

        assertThatThrownBy(() -> unidentified.identifyAs(registeredAdult(), " ", today()))
                .isInstanceOf(PatientException.InvalidData.class);
        assertThatThrownBy(() -> unidentified.identifyAs(inactive, "Cédula", today()))
                .isInstanceOf(PatientException.NotActive.class);
    }

    @Test
    void revertsAMistakenIdentificationKeepingTheReason() {
        UnidentifiedPatient unidentified = arrivedAtEmergencies();
        Patient wrongPatient = registeredAdult();
        unidentified.identifyAs(wrongPatient, "Parecido físico", today());
        unidentified.pullEvents();

        unidentified.revertIdentification("La familia confirmó que no es la persona", today());

        assertThat(unidentified.status()).isEqualTo(new UnidentifiedPatientStatus.Unidentified());
        assertThat(unidentified.statusReason()).isEqualTo("La familia confirmó que no es la persona");
        assertThat(unidentified.pullEvents())
                .containsExactly(new UnidentifiedPatientEvent.IdentificationReverted(wrongPatient.uuid()));
    }

    @Test
    void rejectsInvalidTransitions() {
        UnidentifiedPatient unidentified = arrivedAtEmergencies();

        assertThatThrownBy(() -> unidentified.revertIdentification("Error", today()))
                .isInstanceOf(PatientException.InvalidUnidentifiedStatusTransition.class);

        unidentified.identifyAs(registeredAdult(), "Cédula", today());

        assertThatThrownBy(() -> unidentified.identifyAs(registeredAdult(), "Otra cédula", today()))
                .isInstanceOf(PatientException.InvalidUnidentifiedStatusTransition.class);
        assertThatThrownBy(() -> unidentified.recordDeath(TODAY, today()))
                .isInstanceOf(PatientException.InvalidUnidentifiedStatusTransition.class);
    }

    @Test
    void recordsDeathWhileStillUnidentifiedAndMakesItFinal() {
        UnidentifiedPatient unidentified = pulled(arrivedAtEmergencies());

        assertThatThrownBy(() -> unidentified.recordDeath(TODAY.plusDays(1), today()))
                .isInstanceOf(PatientException.InvalidData.class);

        unidentified.recordDeath(TODAY, today());

        assertThat(unidentified.status()).isEqualTo(new UnidentifiedPatientStatus.Deceased(TODAY));
        assertThat(unidentified.pullEvents()).containsExactly(new UnidentifiedPatientEvent.Died(TODAY));
        assertThatThrownBy(() -> unidentified.identifyAs(registeredAdult(), "Cédula", today()))
                .isInstanceOf(PatientException.InvalidUnidentifiedStatusTransition.class);
    }

    @Test
    void neverPrintsTheDescription() {
        assertThat(arrivedAtEmergencies().toString()).isEqualTo("UnidentifiedPatient[NN-2026-000001]").doesNotContain("cicatriz");
    }

    private static UnidentifiedPatient pulled(UnidentifiedPatient patient) {
        patient.pullEvents();
        return patient;
    }
}
