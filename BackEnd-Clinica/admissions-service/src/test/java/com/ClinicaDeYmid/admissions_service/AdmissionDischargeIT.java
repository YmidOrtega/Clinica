package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.application.CatalogueCommands;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.ServiceType;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReferences;
import com.ClinicaDeYmid.admissions_service.support.TestSequence;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdmissionDischargeIT extends IntegrationTest {

    private static final String BASE = "/api/v1/admissions";
    private static final String EPISODES = BASE + "/episodes";

    @Autowired
    private CatalogueCommands catalogue;

    @Autowired
    private PatientReferences patients;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aVoluntaryDischargeKeepsWhoSignedTheResponsibility() throws Exception {
        String episode = anActiveEpisode();

        change("DOCTOR", post(EPISODES + "/" + episode + "/discharge"), 1,
                """
                {"type":"VOLUNTARY","signedBy":"Carlos Restrepo","signatureDocument":"1094921345"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("DISCHARGED"))
                .andExpect(jsonPath("$.status.discharge.type").value("VOLUNTARY"))
                .andExpect(jsonPath("$.status.discharge.signedBy").value("Carlos Restrepo"))
                .andExpect(jsonPath("$.status.discharge.signatureDocument").value("1094921345"))
                .andExpect(jsonPath("$.status.discharge.at").isNotEmpty());
    }

    @Test
    void aReferralKeepsTheReceivingInstitution() throws Exception {
        String episode = anActiveEpisode();

        change("DOCTOR", post(EPISODES + "/" + episode + "/discharge"), 1,
                """
                {"type":"REFERRAL","repsCode":"050010000101","facility":"Hospital San Vicente",
                 "reason":"Requiere neurocirugía"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.discharge.type").value("REFERRAL"))
                .andExpect(jsonPath("$.status.discharge.repsCode").value("050010000101"))
                .andExpect(jsonPath("$.status.discharge.facility").value("Hospital San Vicente"));
    }

    @Test
    void anEscapeRecordsWhenTheAbsenceWasNoticed() throws Exception {
        String episode = anActiveEpisode();
        String noticed = Instant.now().minusSeconds(1800).toString();

        change("DOCTOR", post(EPISODES + "/" + episode + "/discharge"), 1,
                "{\"type\":\"ESCAPE\",\"noticedAt\":\"" + noticed + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.discharge.type").value("ESCAPE"))
                .andExpect(jsonPath("$.status.discharge.noticedAt").isNotEmpty());
    }

    @Test
    void aDeathKeepsItsCertificateAndFreesTheBed() throws Exception {
        String bed = aBed();
        String episode = admitTo(inpatient());
        change("NURSE", post(EPISODES + "/" + episode + "/bed"), 0, "{\"bedUuid\":\"" + bed + "\"}")
                .andExpect(status().isOk());
        change("RECEPTIONIST", post(EPISODES + "/" + episode + "/activation"), 1, null).andExpect(status().isOk());

        change("DOCTOR", post(EPISODES + "/" + episode + "/discharge"), 2,
                "{\"type\":\"DEATH\",\"occurredAt\":\"" + Instant.now().minusSeconds(600)
                        + "\",\"certificateNumber\":\"CD-2026-0001\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.discharge.type").value("DEATH"))
                .andExpect(jsonPath("$.status.discharge.certificateNumber").value("CD-2026-0001"))
                .andExpect(jsonPath("$.bedUuid").doesNotExist());

        as("NURSE", get(BASE + "/beds/" + bed), null).andExpect(jsonPath("$.status.code").value("CLEANING"));
    }

    @Test
    void everyDischargeDemandsWhatItsTypeNeeds() throws Exception {
        String episode = anActiveEpisode();

        change("DOCTOR", post(EPISODES + "/" + episode + "/discharge"), 1, "{\"type\":\"VOLUNTARY\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMISSIONS_INVALID_DATA"));

        change("DOCTOR", post(EPISODES + "/" + episode + "/discharge"), 1,
                """
                {"type":"REFERRAL","repsCode":"0500","facility":"Hospital San Vicente","reason":"Neurocirugía"}""")
                .andExpect(status().isBadRequest());

        change("DOCTOR", post(EPISODES + "/" + episode + "/discharge"), 1,
                "{\"type\":\"DEATH\",\"occurredAt\":\"" + Instant.now().plusSeconds(3600)
                        + "\",\"certificateNumber\":\"CD-2026-0002\"}")
                .andExpect(status().isBadRequest());

        change("DOCTOR", post(EPISODES + "/" + episode + "/discharge"), 1, "{}")
                .andExpect(status().isBadRequest());

        as("BILLING", get(EPISODES + "/" + episode), null)
                .andExpect(jsonPath("$.status.code").value("ACTIVE"));
    }

    @Test
    void theHistoryKeepsHowTheEpisodeEnded() throws Exception {
        String episode = anActiveEpisode();

        change("DOCTOR", post(EPISODES + "/" + episode + "/discharge"), 1,
                "{\"type\":\"MEDICAL\",\"notes\":\"Egresa caminando\"}")
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM admissions_history.admissions_aud a "
                        + "JOIN admissions.admissions e ON e.id = a.id "
                        + "WHERE e.uuid = ?::uuid AND a.discharge_type = 'MEDICAL'", Integer.class, episode))
                .isEqualTo(1);
    }

    @Test
    void theDatabaseRefusesADischargeThatDoesNotMatchItsType() throws Exception {
        String episode = anActiveEpisode();
        change("DOCTOR", post(EPISODES + "/" + episode + "/discharge"), 1, "{\"type\":\"MEDICAL\"}")
                .andExpect(status().isOk());

        assertThatThrownBy(() -> jdbc.update("UPDATE admissions.admissions SET death_certificate_number = 'CD-999' "
                + "WHERE uuid = ?::uuid", episode))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("UPDATE admissions.admissions SET discharge_type = NULL "
                + "WHERE uuid = ?::uuid", episode))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("UPDATE admissions.admissions "
                + "SET escape_noticed_at = now(), discharge_type = 'ESCAPE', discharge_notes = 'Se fue' "
                + "WHERE uuid = ?::uuid", episode))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anEpisodeThatNeverStartedCannotBeDischarged() throws Exception {
        String episode = admitTo(emergency());

        change("DOCTOR", post(EPISODES + "/" + episode + "/discharge"), 0, "{\"type\":\"MEDICAL\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADMISSION_INVALID_TRANSITION"));
    }

    private String anActiveEpisode() throws Exception {
        String episode = admitTo(emergency());
        change("RECEPTIONIST", post(EPISODES + "/" + episode + "/activation"), 0, null)
                .andExpect(status().isOk());
        return episode;
    }

    private String admitTo(UUID service) throws Exception {
        UUID patient = UUID.randomUUID();
        patients.saveIfNewer(new PatientReference.Registered(patient, 1,
                new PatientReference.Document("CEDULA_DE_CIUDADANIA", "70" + TestSequence.next()),
                "Ana María", "Restrepo Gómez", LocalDate.of(1990, 4, 12), PatientReference.Sex.FEMALE,
                PatientReference.Registered.Status.ACTIVE, null, "CONTRIBUTORY", UUID.randomUUID().toString()));
        String body = as("RECEPTIONIST", post(EPISODES), "{\"patientUuid\":\"" + patient
                + "\",\"configurationServiceUuid\":\"" + service + "\",\"cause\":\"ILLNESS\"}")
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.uuid");
    }

    private String aBed() throws Exception {
        int index = TestSequence.next();
        String location = JsonPath.read(as("ADMIN", post(BASE + "/catalogue/locations"),
                "{\"name\":\"Piso egreso " + index + "\"}").andReturn().getResponse().getContentAsString(), "$.uuid");
        String room = JsonPath.read(as("ADMIN", post(BASE + "/rooms"),
                "{\"name\":\"Hab egreso " + index + "\",\"locationUuid\":\"" + location + "\",\"stayType\":\"GENERAL_WARD\"}")
                .andReturn().getResponse().getContentAsString(), "$.uuid");
        return JsonPath.read(as("ADMIN", post(BASE + "/beds"),
                "{\"label\":\"Cama egreso " + index + "\",\"roomUuid\":\"" + room + "\"}")
                .andReturn().getResponse().getContentAsString(), "$.uuid");
    }

    private UUID emergency() {
        return configured("Urgencias", AdmissionKind.EMERGENCY);
    }

    private UUID inpatient() {
        return configured("Hospitalización", AdmissionKind.INPATIENT);
    }

    private UUID configured(String service, AdmissionKind kind) {
        int index = TestSequence.next();
        ServiceType type = catalogue.defineServiceType(service + " egreso " + index, kind);
        Location where = catalogue.defineLocation("Sede egreso " + index);
        ConfigurationService configured = catalogue.configure(type.uuid(), where.uuid());
        return configured.uuid();
    }
}
