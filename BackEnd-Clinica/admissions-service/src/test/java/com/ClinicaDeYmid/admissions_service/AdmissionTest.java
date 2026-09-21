package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionStatus;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionsException;
import com.ClinicaDeYmid.admissions_service.domain.CareType;
import com.ClinicaDeYmid.admissions_service.domain.Cause;
import com.ClinicaDeYmid.admissions_service.domain.Companion;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.domain.Discharge;
import com.ClinicaDeYmid.admissions_service.domain.EmergencyPhase;
import com.ClinicaDeYmid.admissions_service.domain.InpatientPhase;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.OutpatientPhase;
import com.ClinicaDeYmid.admissions_service.domain.ServiceType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdmissionTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-20T10:15:30Z"), ZoneOffset.UTC);
    private final UUID patient = UUID.randomUUID();

    @Test
    void anEmergencyAdmissionStartsRegisteredInAnEmergencyPhase() {
        Admission admission = admit(emergency());

        assertThat(admission.status()).isInstanceOf(AdmissionStatus.Registered.class);
        assertThat(admission.currentPhase()).isInstanceOf(EmergencyPhase.class);
        assertThat(admission.kind()).isEqualTo(AdmissionKind.EMERGENCY);
        assertThat(admission.bedRequired()).isFalse();
        assertThat(admission.coverageMayBlockAdmission()).isFalse();
    }

    @Test
    void anInpatientAdmissionNeedsABedAndCoverageCanBlockIt() {
        Admission admission = admit(inpatient());

        assertThat(admission.currentPhase()).isInstanceOf(InpatientPhase.class);
        assertThat(admission.bedRequired()).isTrue();
        assertThat(admission.coverageMayBlockAdmission()).isTrue();
    }

    @Test
    void anOutpatientAdmissionNeedsNoBedButCoverageCanBlockIt() {
        Admission admission = admit(outpatient());

        assertThat(admission.currentPhase()).isInstanceOf(OutpatientPhase.class);
        assertThat(admission.bedRequired()).isFalse();
        assertThat(admission.coverageMayBlockAdmission()).isTrue();
    }

    @Test
    void theEpisodeWalksFromRegisteredToActiveToDischarged() {
        Admission admission = admit(emergency());

        admission.activate(clock);
        assertThat(admission.status()).isEqualTo(new AdmissionStatus.Active(Instant.parse("2026-09-20T10:15:30Z")));

        admission.discharge(medicalDischarge());
        assertThat(admission.status()).isInstanceOf(AdmissionStatus.Discharged.class);
        assertThat(admission.status().open()).isFalse();
    }

    @Test
    void anEpisodeCannotBeDischargedBeforeItIsActive() {
        assertThatThrownBy(() -> admit(emergency()).discharge(medicalDischarge()))
                .isInstanceOf(AdmissionsException.InvalidAdmissionTransition.class);
    }

    @Test
    void aDischargedEpisodeIsSealed() {
        Admission admission = admit(emergency());
        admission.activate(clock);
        admission.discharge(medicalDischarge());

        assertThatThrownBy(() -> admission.activate(clock))
                .isInstanceOf(AdmissionsException.InvalidAdmissionTransition.class);
        assertThatThrownBy(() -> admission.cancel("Error de registro", clock))
                .isInstanceOf(AdmissionsException.InvalidAdmissionTransition.class);
    }

    @Test
    void cancellingKeepsTheReasonAndClosesTheCurrentPhase() {
        Admission admission = admit(emergency());

        admission.cancel("Se registró dos veces", clock);

        assertThat(admission.status()).isEqualTo(
                new AdmissionStatus.Cancelled("Se registró dos veces", Instant.parse("2026-09-20T10:15:30Z")));
        assertThat(admission.phases()).allSatisfy(phase -> assertThat(phase.current()).isFalse());
    }

    @Test
    void cancellingDemandsAReason() {
        assertThatThrownBy(() -> admit(emergency()).cancel(" ", clock))
                .isInstanceOf(AdmissionsException.InvalidData.class);
    }

    @Test
    void movingFromEmergencyToWardKeepsOneEpisodeWithTwoPhases() {
        Admission admission = admit(emergency());
        admission.activate(clock);
        String number = admission.number();

        admission.moveTo(inpatient(), "Requiere hospitalización", clock);

        assertThat(admission.number()).isEqualTo(number);
        assertThat(admission.phases()).hasSize(2);
        assertThat(admission.phases().get(0)).isInstanceOf(EmergencyPhase.class);
        assertThat(admission.phases().get(0).current()).isFalse();
        assertThat(admission.currentPhase()).isInstanceOf(InpatientPhase.class);
        assertThat(admission.currentPhase().openingReason()).isEqualTo("Requiere hospitalización");
        assertThat(admission.bedRequired()).isTrue();
    }

    @Test
    void movingToTheSameConfiguredServiceIsRefused() {
        ConfigurationService service = emergency();
        Admission admission = admit(service);

        assertThatThrownBy(() -> admission.moveTo(service, "Sin cambio", clock))
                .isInstanceOf(AdmissionsException.SamePhaseAlreadyCurrent.class);
    }

    @Test
    void aClosedEpisodeCannotChangePhase() {
        Admission admission = admit(emergency());
        admission.cancel("Duplicada", clock);

        assertThatThrownBy(() -> admission.moveTo(inpatient(), "Tarde", clock))
                .isInstanceOf(AdmissionsException.InvalidAdmissionTransition.class);
    }

    @Test
    void anAdmissionCannotOpenInARetiredConfiguredService() {
        ConfigurationService retired = emergency();
        retired.retire("Se cerró el servicio", clock);

        assertThatThrownBy(() -> admit(retired))
                .isInstanceOf(AdmissionsException.RetiredConfigurationService.class);
    }

    @Test
    void theCareTypeMustBelongToTheServiceTypeOfTheAdmission() {
        ServiceType other = ServiceType.define("Consulta externa", AdmissionKind.OUTPATIENT);
        CareType alien = CareType.define("Control", other);

        assertThatThrownBy(() -> Admission.register("ADM-2026-000001", patient, emergency(), Cause.ILLNESS, alien,
                null, clock)).isInstanceOf(AdmissionsException.CareTypeDoesNotBelongToTheService.class);
    }

    @Test
    void aCompanionNeedsANameAndAPhoneNumber() {
        assertThatThrownBy(() -> Companion.of("María Restrepo", " ", "Madre"))
                .isInstanceOf(AdmissionsException.InvalidData.class);

        Companion companion = Companion.of("María Restrepo", "3001234567", "Madre");
        assertThat(companion.fullName()).isEqualTo("María Restrepo");
    }

    private Discharge medicalDischarge() {
        return new Discharge.Medical(Instant.now(clock), "Paciente estable");
    }

    private Admission admit(ConfigurationService service) {
        return Admission.register("ADM-2026-000001", patient, service, Cause.ILLNESS, null, null, clock);
    }

    private ConfigurationService emergency() {
        return ConfigurationService.configure(
                ServiceType.define("Urgencias", AdmissionKind.EMERGENCY), Location.define("Piso 1"));
    }

    private ConfigurationService inpatient() {
        return ConfigurationService.configure(
                ServiceType.define("Hospitalización", AdmissionKind.INPATIENT), Location.define("Piso 3"));
    }

    private ConfigurationService outpatient() {
        return ConfigurationService.configure(
                ServiceType.define("Consulta externa", AdmissionKind.OUTPATIENT), Location.define("Piso 2"));
    }
}
