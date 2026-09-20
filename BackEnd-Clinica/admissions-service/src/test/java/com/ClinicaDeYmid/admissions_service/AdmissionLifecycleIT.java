package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.application.AdmissionCommands;
import com.ClinicaDeYmid.admissions_service.application.BedCommands;
import com.ClinicaDeYmid.admissions_service.application.CatalogueCommands;
import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionStatus;
import com.ClinicaDeYmid.admissions_service.domain.Admissions;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionsException;
import com.ClinicaDeYmid.admissions_service.domain.Cause;
import com.ClinicaDeYmid.admissions_service.domain.Companion;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.domain.EmergencyPhase;
import com.ClinicaDeYmid.admissions_service.domain.InpatientPhase;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.ServiceType;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReferences;
import com.ClinicaDeYmid.admissions_service.support.JwtTestTokens;
import com.ClinicaDeYmid.admissions_service.support.TestSequence;
import com.ClinicaDeYmid.admissions_service.support.StubbedServices;
import com.ClinicaDeYmid.commons.web.EntityTags;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdmissionLifecycleIT extends IntegrationTest {

    @Autowired
    private AdmissionCommands commands;

    @Autowired
    private CatalogueCommands catalogue;

    @Autowired
    private BedCommands bedCommands;

    @Autowired
    private Admissions admissions;

    @Autowired
    private PatientReferences patients;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void registersAnEmergencyAdmissionWithItsOwnNumber() {
        UUID patient = aPatient();
        Admission admission = commands.register(patient, emergency(), Cause.ILLNESS, null, null);

        assertThat(admission.number()).matches("ADM-\\d{4}-\\d{6}");
        assertThat(admission.number()).startsWith("ADM-" + LocalDate.now().getYear());
        assertThat(admission.status()).isInstanceOf(AdmissionStatus.Registered.class);
        assertThat(admission.currentPhase()).isInstanceOf(EmergencyPhase.class);
        assertThat(admissions.findByNumber(admission.number())).isPresent();
    }

    @Test
    void givesEveryAdmissionADifferentNumber() {
        UUID service = emergency();
        String first = commands.register(aPatient(), service, Cause.ILLNESS, null, null).number();
        String second = commands.register(aPatient(), service, Cause.ACCIDENT, null, null).number();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void refusesToAdmitAPatientTheRegistryDoesNotKnow() {
        assertThatThrownBy(() -> commands.register(UUID.randomUUID(), emergency(), Cause.ILLNESS, null, null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void refusesToAdmitADeceasedPatient() {
        UUID patient = aPatient("DECEASED");

        assertThatThrownBy(() -> commands.register(patient, emergency(), Cause.ILLNESS, null, null))
                .isInstanceOf(AdmissionsException.PatientNotAdmissible.class);
    }

    @Test
    void movesFromEmergencyToWardKeepingOneEpisodeAndBothPhases() {
        Admission admission = commands.register(aPatient(), emergency(), Cause.ILLNESS, null, null);
        Admission active = commands.activate(admission.uuid(), admission.version());

        Admission moved = commands.moveTo(active.uuid(), active.version(), inpatient(),
                "Requiere hospitalización", aBed());

        assertThat(moved.number()).isEqualTo(admission.number());
        assertThat(moved.phases()).hasSize(2);
        assertThat(moved.currentPhase()).isInstanceOf(InpatientPhase.class);
        assertThat(moved.bedRequired()).isTrue();
        assertThat(moved.phases().get(0).endedAt()).isNotNull();
    }

    @Test
    void thePhasesOfAnEpisodeNeverOverlapInTheDatabase() {
        Admission admission = commands.register(aPatient(), emergency(), Cause.ILLNESS, null, null);
        long id = jdbc.queryForObject("SELECT id FROM admissions.admissions WHERE uuid = ?::uuid",
                Long.class, admission.uuid().toString());
        long service = jdbc.queryForObject(
                "SELECT configuration_service_id FROM admissions.admission_phases WHERE admission_id = ?",
                Long.class, id);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO admissions.admission_phases (uuid, kind, admission_id, configuration_service_id, started_at) "
                        + "VALUES (gen_random_uuid(), 'INPATIENT', ?, ?, now())", id, service))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void demandsTheCurrentVersionBeforeChangingTheEpisode() {
        Admission admission = commands.register(aPatient(), emergency(), Cause.ILLNESS, null, null);

        assertThatThrownBy(() -> commands.activate(admission.uuid(), admission.version() + 7))
                .isInstanceOf(EntityTags.StaleVersion.class);
    }

    @Test
    void recordsTheCompanionAndKeepsTheEpisodeInEnvers() {
        Admission admission = commands.register(aPatient(), emergency(), Cause.ILLNESS, null,
                Companion.of("María Restrepo", "3001234567", "Madre"));
        commands.activate(admission.uuid(), admission.version());

        Integer revisions = jdbc.queryForObject(
                "SELECT count(*) FROM admissions_history.admissions_aud a "
                        + "JOIN admissions.admissions m ON m.id = a.id WHERE m.uuid = ?::uuid",
                Integer.class, admission.uuid().toString());

        assertThat(revisions).isGreaterThanOrEqualTo(2);
        assertThat(admissions.findByUuid(admission.uuid())).get()
                .satisfies(found -> assertThat(found.companion().fullName()).isEqualTo("María Restrepo"));
    }

    @Test
    void findsTheOpenEpisodeOfAPatientAndStopsFindingItOnceCancelled() {
        UUID patient = aPatient();
        Admission admission = commands.register(patient, emergency(), Cause.ILLNESS, null, null);

        assertThat(admissions.findOpenByPatient(patient)).isPresent();

        commands.cancel(admission.uuid(), admission.version(), "Se registró dos veces");

        assertThat(admissions.findOpenByPatient(patient)).isEmpty();
        assertThat(admissions.findByPatient(patient)).hasSize(1);
    }

    private UUID aBed() {
        int index = TestSequence.next();
        Location where = catalogue.defineLocation("Piso cama " + index);
        return bedCommands.installBed("Cama " + index,
                bedCommands.openRoom("Hab " + index, where.uuid()).uuid()).uuid();
    }

    private UUID aPatient() {
        return aPatient("ACTIVE");
    }

    private UUID aPatient(String status) {
        UUID uuid = UUID.randomUUID();
        patients.saveIfNewer(new PatientReference.Registered(uuid, 1,
                new PatientReference.Document("CEDULA_DE_CIUDADANIA", "10" + TestSequence.next()),
                "Ana María", "Restrepo Gómez", LocalDate.of(1990, 4, 12), PatientReference.Sex.FEMALE,
                PatientReference.Registered.Status.valueOf(status),
                "DECEASED".equals(status) ? LocalDate.of(2026, 9, 19) : null, "CONTRIBUTORY", null));
        return uuid;
    }

    private UUID emergency() {
        return configured("Urgencias", AdmissionKind.EMERGENCY, "Piso 1");
    }

    private UUID inpatient() {
        return configured("Hospitalización", AdmissionKind.INPATIENT, "Piso 3");
    }

    private UUID configured(String service, AdmissionKind kind, String location) {
        int index = TestSequence.next();
        ServiceType type = catalogue.defineServiceType(service + " " + index, kind);
        Location where = catalogue.defineLocation(location + " " + index);
        ConfigurationService configured = catalogue.configure(type.uuid(), where.uuid());
        return configured.uuid();
    }
}
