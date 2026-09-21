package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.application.CatalogueCommands;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.ServiceType;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReferences;
import com.ClinicaDeYmid.admissions_service.support.JwtTestTokens;
import com.ClinicaDeYmid.admissions_service.support.TestSequence;
import com.ClinicaDeYmid.admissions_service.support.StubbedServices;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdmissionBedIT extends IntegrationTest {

    private static final String BASE = "/api/v1/admissions";
    private static final String EPISODES = BASE + "/episodes";
    @Autowired
    private CatalogueCommands catalogue;

    @Autowired
    private PatientReferences patients;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void anInpatientEpisodeCannotBeActivatedWithoutABed() throws Exception {
        String episode = admitTo(inpatient());

        change("RECEPTIONIST", post(EPISODES + "/" + episode + "/activation"), 0, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADMISSION_BED_REQUIRED"));

        String bed = aBed();
        change("NURSE", post(EPISODES + "/" + episode + "/bed"), 0, "{\"bedUuid\":\"" + bed + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bedUuid").value(bed));

        change("RECEPTIONIST", post(EPISODES + "/" + episode + "/activation"), 1, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("ACTIVE"));
    }

    @Test
    void anEmergencyEpisodeActivatesWithoutABed() throws Exception {
        String episode = admitTo(emergency());

        change("RECEPTIONIST", post(EPISODES + "/" + episode + "/activation"), 0, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bedUuid").doesNotExist());
    }

    @Test
    void takingABedMarksItOccupiedByTheEpisode() throws Exception {
        String episode = admitTo(inpatient());
        String bed = aBed();

        change("NURSE", post(EPISODES + "/" + episode + "/bed"), 0, "{\"bedUuid\":\"" + bed + "\"}")
                .andExpect(status().isOk());

        as("NURSE", get(BASE + "/beds/" + bed))
                .andExpect(jsonPath("$.status.code").value("OCCUPIED"))
                .andExpect(jsonPath("$.status.occupant").value(episode));
    }

    @Test
    void transferringLeavesTheOldBedInCleaningAndTakesTheNewOne() throws Exception {
        String episode = admitTo(inpatient());
        String first = aBed();
        String second = aBed();

        change("NURSE", post(EPISODES + "/" + episode + "/bed"), 0, "{\"bedUuid\":\"" + first + "\"}")
                .andExpect(status().isOk());
        change("NURSE", post(EPISODES + "/" + episode + "/bed"), 1, "{\"bedUuid\":\"" + second + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bedUuid").value(second));

        as("NURSE", get(BASE + "/beds/" + first)).andExpect(jsonPath("$.status.code").value("CLEANING"));
        as("NURSE", get(BASE + "/beds/" + second)).andExpect(jsonPath("$.status.code").value("OCCUPIED"));

        assertThat(staysOf(episode)).isEqualTo(2);
    }

    @Test
    void twoEpisodesCannotShareTheSameBed() throws Exception {
        String bed = aBed();
        String first = admitTo(inpatient());
        String second = admitTo(inpatient());

        change("NURSE", post(EPISODES + "/" + first + "/bed"), 0, "{\"bedUuid\":\"" + bed + "\"}")
                .andExpect(status().isOk());
        change("NURSE", post(EPISODES + "/" + second + "/bed"), 0, "{\"bedUuid\":\"" + bed + "\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void dischargingFreesTheBedByItself() throws Exception {
        String episode = admitTo(inpatient());
        String bed = aBed();
        change("NURSE", post(EPISODES + "/" + episode + "/bed"), 0, "{\"bedUuid\":\"" + bed + "\"}")
                .andExpect(status().isOk());
        change("RECEPTIONIST", post(EPISODES + "/" + episode + "/activation"), 1, null).andExpect(status().isOk());

        change("DOCTOR", post(EPISODES + "/" + episode + "/discharge"), 2, "{\"type\":\"MEDICAL\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("DISCHARGED"))
                .andExpect(jsonPath("$.bedUuid").doesNotExist());

        as("NURSE", get(BASE + "/beds/" + bed)).andExpect(jsonPath("$.status.code").value("CLEANING"));
    }

    @Test
    void cancellingAlsoFreesTheBed() throws Exception {
        String episode = admitTo(inpatient());
        String bed = aBed();
        change("NURSE", post(EPISODES + "/" + episode + "/bed"), 0, "{\"bedUuid\":\"" + bed + "\"}")
                .andExpect(status().isOk());

        change("ADMIN", post(EPISODES + "/" + episode + "/cancellation"), 1, "{\"reason\":\"Se registró dos veces\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bedUuid").doesNotExist());

        as("NURSE", get(BASE + "/beds/" + bed)).andExpect(jsonPath("$.status.code").value("CLEANING"));
    }

    @Test
    void movingFromEmergencyToTheWardDemandsABedInTheSameCall() throws Exception {
        String episode = admitTo(emergency());
        change("RECEPTIONIST", post(EPISODES + "/" + episode + "/activation"), 0, null).andExpect(status().isOk());

        change("RECEPTIONIST", post(EPISODES + "/" + episode + "/phase"), 1,
                "{\"configurationServiceUuid\":\"" + inpatient() + "\",\"reason\":\"Requiere hospitalización\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADMISSION_BED_REQUIRED"));

        String bed = aBed();
        change("RECEPTIONIST", post(EPISODES + "/" + episode + "/phase"), 1,
                "{\"configurationServiceUuid\":\"" + inpatient() + "\",\"reason\":\"Requiere hospitalización\","
                        + "\"bedUuid\":\"" + bed + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("INPATIENT"))
                .andExpect(jsonPath("$.bedUuid").value(bed));
    }

    @Test
    void goingBackToAnAmbulatoryPhaseFreesTheBed() throws Exception {
        String episode = admitTo(inpatient());
        String bed = aBed();
        change("NURSE", post(EPISODES + "/" + episode + "/bed"), 0, "{\"bedUuid\":\"" + bed + "\"}")
                .andExpect(status().isOk());
        change("RECEPTIONIST", post(EPISODES + "/" + episode + "/activation"), 1, null).andExpect(status().isOk());

        change("RECEPTIONIST", post(EPISODES + "/" + episode + "/phase"), 2,
                "{\"configurationServiceUuid\":\"" + outpatient() + "\",\"reason\":\"Pasa a consulta externa\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("OUTPATIENT"))
                .andExpect(jsonPath("$.bedUuid").doesNotExist());

        as("NURSE", get(BASE + "/beds/" + bed)).andExpect(jsonPath("$.status.code").value("CLEANING"));
    }

    @Test
    void onlyNursingMovesEpisodesBetweenBeds() throws Exception {
        String episode = admitTo(inpatient());

        change("BILLING", post(EPISODES + "/" + episode + "/bed"), 0, "{\"bedUuid\":\"" + aBed() + "\"}")
                .andExpect(status().isForbidden());
    }

    private Integer staysOf(String episode) {
        return jdbc.queryForObject("SELECT count(*) FROM admissions.bed_stays WHERE occupant_uuid = ?::uuid",
                Integer.class, episode);
    }

    private String admitTo(UUID service) throws Exception {
        UUID patient = UUID.randomUUID();
        patients.saveIfNewer(new PatientReference.Registered(patient, 1,
                new PatientReference.Document("CEDULA_DE_CIUDADANIA", "50" + TestSequence.next()),
                "Ana María", "Restrepo Gómez", LocalDate.of(1990, 4, 12), PatientReference.Sex.FEMALE,
                PatientReference.Registered.Status.ACTIVE, null, "CONTRIBUTORY", UUID.randomUUID().toString()));
        String body = as("RECEPTIONIST", post(EPISODES).content("{\"patientUuid\":\"" + patient
                + "\",\"configurationServiceUuid\":\"" + service + "\",\"cause\":\"ILLNESS\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.uuid");
    }

    private String aBed() throws Exception {
        int index = TestSequence.next();
        String location = JsonPath.read(as("ADMIN", post("/api/v1/admissions/catalogue/locations")
                .content("{\"name\":\"Piso cama " + index + "\"}")).andReturn().getResponse().getContentAsString(),
                "$.uuid");
        String room = JsonPath.read(as("ADMIN", post(BASE + "/rooms")
                .content("{\"name\":\"Hab " + index + "\",\"locationUuid\":\"" + location + "\"}"))
                .andReturn().getResponse().getContentAsString(), "$.uuid");
        return JsonPath.read(as("ADMIN", post(BASE + "/beds")
                .content("{\"label\":\"Cama " + index + "\",\"roomUuid\":\"" + room + "\"}"))
                .andReturn().getResponse().getContentAsString(), "$.uuid");
    }

    private UUID emergency() {
        return configured("Urgencias", AdmissionKind.EMERGENCY);
    }

    private UUID inpatient() {
        return configured("Hospitalización", AdmissionKind.INPATIENT);
    }

    private UUID outpatient() {
        return configured("Consulta externa", AdmissionKind.OUTPATIENT);
    }

    private UUID configured(String service, AdmissionKind kind) {
        int index = TestSequence.next();
        ServiceType type = catalogue.defineServiceType(service + " " + index, kind);
        Location where = catalogue.defineLocation("Sede " + index);
        ConfigurationService configured = catalogue.configure(type.uuid(), where.uuid());
        return configured.uuid();
    }

}
