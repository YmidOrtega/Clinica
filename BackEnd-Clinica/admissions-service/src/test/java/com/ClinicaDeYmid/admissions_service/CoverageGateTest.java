package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.application.coverage.CoverageChecker;
import com.ClinicaDeYmid.admissions_service.application.coverage.CoverageGate;
import com.ClinicaDeYmid.admissions_service.application.coverage.CoverageVerdict;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionsException;
import com.ClinicaDeYmid.admissions_service.domain.Coverage;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoverageGateTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-20T10:15:30Z"), ZoneOffset.UTC);
    private final UUID contract = UUID.randomUUID();
    private final UUID payer = UUID.randomUUID();

    @ParameterizedTest
    @EnumSource(AdmissionKind.class)
    void aCoveredPatientIsAdmittedWhateverTheKind(AdmissionKind kind) {
        Coverage coverage = gate(new CoverageVerdict.Covered(contract, "CT-001", payer)).assess(patient(), kind, false);

        assertThat(coverage.status()).isEqualTo(Coverage.Code.COVERED);
        assertThat(coverage.contractNumber()).isEqualTo("CT-001");
        assertThat(coverage.pending()).isFalse();
    }

    @Test
    void anEmergencyIsNeverBlockedForLackOfCoverage() {
        Coverage coverage = gate(new CoverageVerdict.NotCovered("sin contrato vigente", payer))
                .assess(patient(), AdmissionKind.EMERGENCY, false);

        assertThat(coverage.status()).isEqualTo(Coverage.Code.NOT_COVERED);
        assertThat(coverage.detail()).isEqualTo("sin contrato vigente");
        assertThat(coverage.pending()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = AdmissionKind.class, names = {"INPATIENT", "OUTPATIENT"})
    void theOtherKindsAreBlockedWhenThereIsNoCoverage(AdmissionKind kind) {
        CoverageGate gate = gate(new CoverageVerdict.NotCovered("sin contrato vigente", payer));

        assertThatThrownBy(() -> gate.assess(patient(), kind, false))
                .isInstanceOf(AdmissionsException.WithoutCoverage.class)
                .hasMessageContaining("sin contrato vigente");
    }

    @ParameterizedTest
    @EnumSource(value = AdmissionKind.class, names = {"INPATIENT", "OUTPATIENT"})
    void anAdministratorCanOverrideTheBlock(AdmissionKind kind) {
        Coverage coverage = gate(new CoverageVerdict.NotCovered("sin contrato vigente", payer))
                .assess(patient(), kind, true);

        assertThat(coverage.status()).isEqualTo(Coverage.Code.NOT_COVERED);
        assertThat(coverage.pending()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(AdmissionKind.class)
    void nobodyIsBlockedWhenContractingDoesNotAnswer(AdmissionKind kind) {
        Coverage coverage = gate(new CoverageVerdict.Unknown("contracting-service no respondió", payer))
                .assess(patient(), kind, false);

        assertThat(coverage.status()).isEqualTo(Coverage.Code.UNKNOWN);
        assertThat(coverage.pending()).isTrue();
        assertThat(coverage.checkedAt()).isEqualTo(Instant.parse("2026-09-20T10:15:30Z"));
    }

    private CoverageGate gate(CoverageVerdict verdict) {
        CoverageChecker checker = (patient, on) -> verdict;
        return new CoverageGate(checker, clock);
    }

    private PatientReference patient() {
        return new PatientReference.Registered(UUID.randomUUID(), 1,
                new PatientReference.Document("CEDULA_DE_CIUDADANIA", "1098765432"), "Ana María", "Restrepo",
                LocalDate.of(1990, 4, 12), PatientReference.Sex.FEMALE,
                PatientReference.Registered.Status.ACTIVE, null, "CONTRIBUTORY", payer.toString());
    }
}
