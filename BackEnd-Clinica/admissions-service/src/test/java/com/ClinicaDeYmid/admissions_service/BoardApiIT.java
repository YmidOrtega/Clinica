package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.application.CatalogueCommands;
import com.ClinicaDeYmid.admissions_service.application.TriageReflection;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.ServiceType;
import com.ClinicaDeYmid.admissions_service.domain.Triage;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReferences;
import com.ClinicaDeYmid.admissions_service.support.TestSequence;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BoardApiIT extends IntegrationTest {

    private static final String BASE = "/api/v1/admissions";
    private static final String EPISODES = BASE + "/episodes";

    @Autowired
    private CatalogueCommands catalogue;

    @Autowired
    private PatientReferences patients;

    @Autowired
    private TriageReflection triage;

    @Test
    void theCensusShowsEveryBedAndWhoIsInIt() throws Exception {
        UUID location = aLocation();
        String taken = aBedIn(location);
        String free = aBedIn(location);
        UUID patient = aLocalPatient();
        UUID service = configured(location, AdmissionKind.INPATIENT);
        String episode = anEpisode(patient, service);
        change("NURSE", post(EPISODES + "/" + episode + "/bed"), 0, "{\"bedUuid\":\"" + taken + "\"}")
                .andExpect(status().isOk());

        as("NURSE", get(BASE + "/locations/" + location + "/census"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.bedUuid=='" + taken + "')].status").value("OCCUPIED"))
                .andExpect(jsonPath("$[?(@.bedUuid=='" + taken + "')].occupant.uuid").value(episode))
                .andExpect(jsonPath("$[?(@.bedUuid=='" + taken + "')].occupant.patientUuid")
                        .value(patient.toString()))
                .andExpect(jsonPath("$[?(@.bedUuid=='" + taken + "')].since").isNotEmpty())
                .andExpect(jsonPath("$[?(@.bedUuid=='" + free + "')].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[?(@.bedUuid=='" + free + "')].occupant.uuid").doesNotExist());
    }

    @Test
    void theQueueHoldsTheOpenEpisodesOfTheServiceUntilTheyLeaveIt() throws Exception {
        UUID service = configured(aLocation(), AdmissionKind.EMERGENCY);
        String waiting = anEpisode(aLocalPatient(), service);
        String attended = anEpisode(aLocalPatient(), service);
        change("RECEPTIONIST", post(EPISODES + "/" + attended + "/activation"), 0, null).andExpect(status().isOk());

        as("DOCTOR", get(BASE + "/configured-services/" + service + "/queue"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].uuid").value(waiting))
                .andExpect(jsonPath("$[0].status").value("REGISTERED"))
                .andExpect(jsonPath("$[1].uuid").value(attended))
                .andExpect(jsonPath("$[1].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].configurationServiceUuid").value(service.toString()));

        change("DOCTOR", post(EPISODES + "/" + attended + "/discharge"), 1, "{\"type\":\"MEDICAL\"}")
                .andExpect(status().isOk());

        as("DOCTOR", get(BASE + "/configured-services/" + service + "/queue"), null)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].uuid").value(waiting));
    }

    @Test
    void anEpisodeLeavesTheQueueOfTheServiceItMovesOutOf() throws Exception {
        UUID location = aLocation();
        UUID emergency = configured(location, AdmissionKind.EMERGENCY);
        UUID ward = configured(location, AdmissionKind.INPATIENT);
        String episode = anEpisode(aLocalPatient(), emergency);
        change("RECEPTIONIST", post(EPISODES + "/" + episode + "/activation"), 0, null).andExpect(status().isOk());

        change("RECEPTIONIST", post(EPISODES + "/" + episode + "/phase"), 1,
                "{\"configurationServiceUuid\":\"" + ward + "\",\"reason\":\"Requiere hospitalización\","
                        + "\"bedUuid\":\"" + aBedIn(location) + "\"}")
                .andExpect(status().isOk());

        as("DOCTOR", get(BASE + "/configured-services/" + emergency + "/queue"), null)
                .andExpect(jsonPath("$.length()").value(0));
        as("DOCTOR", get(BASE + "/configured-services/" + ward + "/queue"), null)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].uuid").value(episode))
                .andExpect(jsonPath("$[0].kind").value("INPATIENT"));
    }

    @Test
    void theQueuePutsThoseWaitingForTriageFirstAndThenOrdersByLevel() throws Exception {
        UUID service = configured(aLocation(), AdmissionKind.EMERGENCY);
        String mild = anEpisode(aLocalPatient(), service);
        String critical = anEpisode(aLocalPatient(), service);
        String untriaged = anEpisode(aLocalPatient(), service);

        triage.apply(UUID.fromString(mild), Triage.Level.IV, Instant.now().minusSeconds(120), UUID.randomUUID());
        triage.apply(UUID.fromString(critical), Triage.Level.I, Instant.now().minusSeconds(60), UUID.randomUUID());

        as("DOCTOR", get(BASE + "/configured-services/" + service + "/queue"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].uuid").value(untriaged))
                .andExpect(jsonPath("$[0].triage").doesNotExist())
                .andExpect(jsonPath("$[1].uuid").value(critical))
                .andExpect(jsonPath("$[1].triage.level").value("I"))
                .andExpect(jsonPath("$[1].triage.at").isNotEmpty())
                .andExpect(jsonPath("$[2].uuid").value(mild))
                .andExpect(jsonPath("$[2].triage.level").value("IV"));
    }

    private String anEpisode(UUID patient, UUID service) throws Exception {
        String body = as("RECEPTIONIST", post(EPISODES), "{\"patientUuid\":\"" + patient
                + "\",\"configurationServiceUuid\":\"" + service + "\",\"cause\":\"ILLNESS\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.uuid");
    }

    private UUID aLocalPatient() {
        UUID uuid = UUID.randomUUID();
        patients.saveIfNewer(new PatientReference.Registered(uuid, 1,
                new PatientReference.Document("CEDULA_DE_CIUDADANIA", "60" + TestSequence.next()),
                "Ana María", "Restrepo Gómez", LocalDate.of(1990, 4, 12), PatientReference.Sex.FEMALE,
                PatientReference.Registered.Status.ACTIVE, null, "CONTRIBUTORY", UUID.randomUUID().toString()));
        return uuid;
    }

    private UUID aLocation() {
        return catalogue.defineLocation("Piso tablero " + TestSequence.next()).uuid();
    }

    private String aBedIn(UUID location) throws Exception {
        int index = TestSequence.next();
        String room = JsonPath.read(as("ADMIN", post(BASE + "/rooms"),
                "{\"name\":\"Hab tablero " + index + "\",\"locationUuid\":\"" + location + "\",\"stayType\":\"GENERAL_WARD\"}")
                .andReturn().getResponse().getContentAsString(), "$.uuid");
        return JsonPath.read(as("ADMIN", post(BASE + "/beds"),
                "{\"label\":\"Cama tablero " + index + "\",\"roomUuid\":\"" + room + "\"}")
                .andReturn().getResponse().getContentAsString(), "$.uuid");
    }

    private UUID configured(UUID location, AdmissionKind kind) {
        int index = TestSequence.next();
        ServiceType type = catalogue.defineServiceType(kind + " tablero " + index, kind);
        return catalogue.configure(type.uuid(), location).uuid();
    }
}
