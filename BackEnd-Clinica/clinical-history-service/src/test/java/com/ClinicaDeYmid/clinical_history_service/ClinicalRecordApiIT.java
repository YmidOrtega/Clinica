package com.ClinicaDeYmid.clinical_history_service;

import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReferences;
import com.ClinicaDeYmid.clinical_history_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.clinical_history_service.support.TestJwt;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainer.class)
class ClinicalRecordApiIT {

    private static final String BASE = "/api/v1/clinical";
    private static final String TRIAGE = """
            {"content": {"type": "TRIAGE", "level": "II", "reason": "Dolor torácico opresivo"}}""";
    private static final String DISCHARGE = """
            {"content": {"type": "DISCHARGE", "admissionSummary": "Dolor torácico", "evolutionSummary": "Troponinas negativas",
                         "dischargeCondition": "Estable", "recommendations": "Evitar esfuerzos", "followUp": "Cardiología en 15 días"}}""";

    @RegisterExtension
    static WireMockExtension patientService = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PatientReferences patients;


    private final Staff nurse = new Staff("NURSE");
    private final Staff doctor = new Staff("DOCTOR");
    private final Staff otherDoctor = new Staff("DOCTOR");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.patient-service.url", patientService::baseUrl);
        registry.add("clinica.clinical.patient-events.enabled", () -> false);
        registry.add("spring.kafka.admin.auto-create", () -> false);
        registry.add("clinica.security.jwt.public-key", TestJwt::publicKeyBase64);
        registry.add("eureka.client.enabled", () -> false);
    }

    @Test
    void emergencyCareFromTriageToDischarge() throws Exception {
        String encounter = openEncounter(nurse, activePatient(), "EMERGENCY");

        signedNote(nurse, encounter, TRIAGE);
        String draft = startDraft(doctor, encounter, """
                {"content": {"type": "PROGRESS", "subjective": "Dolor 8/10"}}""");

        doctor.perform(post(BASE + "/drafts/" + draft + "/signature").header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NOTE_INCOMPLETE"));
        doctor.perform(put(BASE + "/drafts/" + draft).header(HttpHeaders.IF_MATCH, "\"0\"").content("""
                        {"content": {"type": "PROGRESS", "subjective": "Dolor 8/10", "objective": "TA 150/90",
                                     "assessment": "Síndrome coronario en estudio", "plan": "Troponinas seriadas"}}"""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""));
        doctor.perform(post(BASE + "/drafts/" + draft + "/signature"))
                .andExpect(status().isPreconditionRequired());
        doctor.perform(post(BASE + "/drafts/" + draft + "/signature").header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isPreconditionFailed());
        doctor.perform(post(BASE + "/drafts/" + draft + "/signature").header(HttpHeaders.IF_MATCH, "\"1\""))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, BASE + "/notes/" + draft))
                .andExpect(jsonPath("$.content.assessment").value("Síndrome coronario en estudio"))
                .andExpect(jsonPath("$.extemporaneous").value(false));

        doctor.perform(post(BASE + "/encounters/" + encounter + "/closure"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ENCOUNTER_NOT_READY_TO_CLOSE"));
        signedNote(doctor, encounter, DISCHARGE);
        doctor.perform(post(BASE + "/encounters/" + encounter + "/closure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("CLOSED"))
                .andExpect(jsonPath("$.status.closedBy.uuid").value(doctor.uuid.toString()));

        nurse.perform(get(BASE + "/encounters/" + encounter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes[*].type", contains("TRIAGE", "PROGRESS", "DISCHARGE")))
                .andExpect(jsonPath("$.notes[0].author.role").value("NURSE"));
        startDraftRequest(doctor, encounter, TRIAGE)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ENCOUNTER_CLOSED"));
    }

    @Test
    void signingNeedsARecentSession() throws Exception {
        String encounter = openEncounter(nurse, activePatient(), "EMERGENCY");
        String draft = startDraft(nurse, encounter, TRIAGE);

        nurse.performWithSessionFrom(Instant.now().minus(Duration.ofMinutes(20)),
                        post(BASE + "/drafts/" + draft + "/signature").header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RECENT_AUTHENTICATION_REQUIRED"));
        nurse.perform(post(BASE + "/drafts/" + draft + "/signature").header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.email").value("nurse@clinica.test"));
    }

    @Test
    void closedEncountersAreClarifiedWithAddendaFromTheSameProfile() throws Exception {
        String encounter = openEncounter(doctor, activePatient(), "OUTPATIENT");
        String note = signedNote(doctor, encounter, """
                {"content": {"type": "CONSULTATION", "specialty": "Medicina interna", "reason": "Control",
                             "findings": "Sin hallazgos", "recommendations": "Continuar"}}""");
        doctor.perform(post(BASE + "/encounters/" + encounter + "/closure")).andExpect(status().isOk());
        String addendum = "{\"content\": {\"type\": \"ADDENDUM\", \"amendsNoteId\": \"" + note + "\", \"text\": \"Se omitió la glucemia: 95 mg/dL\"}}";

        startDraftRequest(nurse, encounter, addendum)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_AMENDMENT"));
        String signed = signedNote(otherDoctor, encounter, addendum);

        doctor.perform(get(BASE + "/encounters/" + encounter))
                .andExpect(jsonPath("$.notes[1].id").value(signed))
                .andExpect(jsonPath("$.notes[1].amendsNoteId").value(note));
        doctor.perform(post(BASE + "/notes/" + note + "/void").content("{\"reason\": \"Error\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ENCOUNTER_CLOSED"));
    }

    @Test
    void draftsArePrivateToTheirAuthor() throws Exception {
        String encounter = openEncounter(doctor, activePatient(), "INPATIENT");
        String draft = startDraft(doctor, encounter, TRIAGE);

        otherDoctor.perform(get(BASE + "/drafts/" + draft))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTE_DRAFT_NOT_FOUND"));
        otherDoctor.perform(post(BASE + "/drafts/" + draft + "/signature").header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isNotFound());
        otherDoctor.perform(get(BASE + "/drafts"))
                .andExpect(jsonPath("$[*].id", not(hasItem(draft))));
        doctor.perform(get(BASE + "/drafts"))
                .andExpect(jsonPath("$[*].id", hasItem(draft)));
        doctor.perform(get(BASE + "/drafts/" + draft))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.content.level").value("II"));
        doctor.perform(delete(BASE + "/drafts/" + draft).header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isNoContent());
        doctor.perform(get(BASE + "/drafts/" + draft))
                .andExpect(status().isNotFound());
    }

    @Test
    void authorsVoidTheirNotesWhichStayVisible() throws Exception {
        String encounter = openEncounter(nurse, activePatient(), "EMERGENCY");
        String note = signedNote(nurse, encounter, """
                {"content": {"type": "NURSING", "observations": "Paciente agitado", "careProvided": "Contención verbal"}}""");

        new Staff("NURSE").perform(post(BASE + "/notes/" + note + "/void").content("{\"reason\": \"Otro paciente\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_THE_AUTHOR"));
        nurse.perform(post(BASE + "/notes/" + note + "/void").content("{\"reason\": \"Registrada en el paciente equivocado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voidedBy.uuid").value(nurse.uuid.toString()));
        nurse.perform(post(BASE + "/notes/" + note + "/void").content("{\"reason\": \"Otra vez\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTE_ALREADY_VOIDED"));

        doctor.perform(get(BASE + "/notes/" + note))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.observations").value("Paciente agitado"))
                .andExpect(jsonPath("$.voided.reason").value("Registrada en el paciente equivocado"));
        doctor.perform(post(BASE + "/encounters/" + encounter + "/closure"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void onlyClinicalStaffWorksWithTheRecordAndEachProfileWithItsNotes() throws Exception {
        UUID patient = activePatient();
        new Staff("RECEPTIONIST").perform(post(BASE + "/encounters").content(openRequest(patient, "OUTPATIENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/drafts")).andExpect(status().isUnauthorized());

        String encounter = openEncounter(nurse, patient, "OUTPATIENT");
        startDraftRequest(nurse, encounter, "{\"content\": {\"type\": \"DISCHARGE\"}}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOTE_TYPE_NOT_ALLOWED_FOR_ROLE"));
        startDraftRequest(nurse, encounter, "{\"content\": {\"type\": \"NURSING\", \"careProvidedd\": \"x\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLINICAL_INVALID_DATA"))
                .andExpect(jsonPath("$.detail").value("El campo 'content.careProvidedd' no pertenece a este tipo de nota"));
        startDraftRequest(nurse, encounter, "{\"content\": {\"type\": \"NURSING\"}, \"occurredAt\": \"2099-01-01T00:00:00Z\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OCCURRENCE_TIME"));
    }

    @Test
    void opensEncountersOnlyForKnownPatientsThatAcceptThem() throws Exception {
        UUID unknown = UUID.randomUUID();
        UUID unreachable = UUID.randomUUID();
        patientService.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathMatching(".*/" + unknown))
                .willReturn(aResponse().withStatus(404)));
        patientService.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathMatching(".*/" + unreachable))
                .willReturn(aResponse().withStatus(500)));

        doctor.perform(post(BASE + "/encounters").content(openRequest(unknown, "OUTPATIENT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLINICAL_PATIENT_NOT_FOUND"));
        doctor.perform(post(BASE + "/encounters").content(openRequest(unreachable, "OUTPATIENT")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PATIENT_REGISTRY_UNAVAILABLE"));
        doctor.perform(post(BASE + "/encounters").content(openRequest(patient(PatientReference.Registered.Status.INACTIVE), "OUTPATIENT")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_ACCEPTING_ENCOUNTERS"));
        doctor.perform(post(BASE + "/encounters").content("{\"type\": \"OUTPATIENT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void thePatientHistoryIncludesCareReceivedWhileUnidentified() throws Exception {
        UUID realPatient = activePatient();
        UUID provisional = UUID.randomUUID();
        patients.saveIfNewer(new PatientReference.Unidentified(provisional, 0, "NN-2026-000321", PatientReference.Sex.MALE, 1970,
                PatientReference.Unidentified.Status.UNIDENTIFIED, null, null));
        String emergency = openEncounter(nurse, provisional, "EMERGENCY");
        String control = openEncounter(doctor, realPatient, "OUTPATIENT");
        patients.saveIfNewer(new PatientReference.Unidentified(provisional, 1, "NN-2026-000321", PatientReference.Sex.MALE, 1970,
                PatientReference.Unidentified.Status.IDENTIFIED, realPatient, null));

        doctor.perform(get(BASE + "/patients/" + realPatient + "/encounters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", contains(control, emergency)))
                .andExpect(jsonPath("$[1].patientUuid").value(provisional.toString()))
                .andExpect(jsonPath("$[1].status.closedAt").doesNotExist());
        doctor.perform(post(BASE + "/encounters").content(openRequest(provisional, "EMERGENCY")))
                .andExpect(status().isUnprocessableEntity());
    }

    private UUID activePatient() {
        return patient(PatientReference.Registered.Status.ACTIVE);
    }

    private UUID patient(PatientReference.Registered.Status status) {
        UUID uuid = UUID.randomUUID();
        patients.saveIfNewer(new PatientReference.Registered(uuid, 0, new PatientReference.Document("CEDULA_DE_CIUDADANIA", "1098765432"),
                "Ana María", "Restrepo Gómez", LocalDate.of(1990, 4, 12), PatientReference.Sex.FEMALE, status, null, "CONTRIBUTORY",
                "900123456-7"));
        return uuid;
    }

    private String openEncounter(Staff staff, UUID patient, String type) throws Exception {
        String body = staff.perform(post(BASE + "/encounters").content(openRequest(patient, type)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status.code").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static String openRequest(UUID patient, String type) {
        return "{\"patientUuid\": \"" + patient + "\", \"type\": \"" + type + "\"}";
    }

    private ResultActions startDraftRequest(Staff staff, String encounter, String body) throws Exception {
        return staff.perform(post(BASE + "/encounters/" + encounter + "/drafts").content(body));
    }

    private String startDraft(Staff staff, String encounter, String body) throws Exception {
        String response = startDraftRequest(staff, encounter, body)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String signedNote(Staff staff, String encounter, String body) throws Exception {
        String draft = startDraft(staff, encounter, body);
        staff.perform(post(BASE + "/drafts/" + draft + "/signature").header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isCreated());
        return draft;
    }

    private final class Staff {

        private final String role;
        private final UUID uuid = UUID.randomUUID();

        private Staff(String role) {
            this.role = role;
        }

        ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
            return performWithSessionFrom(Instant.now(), request);
        }

        ResultActions performWithSessionFrom(Instant issuedAt, MockHttpServletRequestBuilder request) throws Exception {
            return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(role, uuid, issuedAt))
                    .contentType(MediaType.APPLICATION_JSON));
        }
    }
}
