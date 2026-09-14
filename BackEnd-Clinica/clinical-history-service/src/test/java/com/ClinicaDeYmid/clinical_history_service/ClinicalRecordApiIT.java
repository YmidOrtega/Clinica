package com.ClinicaDeYmid.clinical_history_service;

import com.ClinicaDeYmid.clinical_history_service.application.encounter.EncounterCommands;
import com.ClinicaDeYmid.clinical_history_service.application.integrity.IntegrityQueries;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.ClinicalRole;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterType;
import com.ClinicaDeYmid.clinical_history_service.domain.attachment.Attachment;
import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.attachment.AttachmentMaintenance;
import com.ClinicaDeYmid.clinical_history_service.domain.terminology.TerminologyRelease;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.terminology.Cie10Importer;
import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReferences;
import com.ClinicaDeYmid.clinical_history_service.support.AccessAuditContract;
import com.ClinicaDeYmid.clinical_history_service.support.Cie10WorkbookFixture;
import com.ClinicaDeYmid.clinical_history_service.support.MinioTestContainer;
import com.ClinicaDeYmid.clinical_history_service.support.SampleFiles;
import com.ClinicaDeYmid.clinical_history_service.support.ClinicalTestProperties;
import com.ClinicaDeYmid.clinical_history_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.clinical_history_service.support.TestEncryptionKeys;
import com.ClinicaDeYmid.clinical_history_service.support.TestJwt;
import com.ClinicaDeYmid.clinical_history_service.support.TestSealKeys;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TRIAGE = """
            {"content": {"type": "TRIAGE", "level": "II", "reason": "Dolor torácico opresivo"}}""";
    private static final String DISCHARGE = """
            {"content": {"type": "DISCHARGE", "admissionSummary": "Dolor torácico", "evolutionSummary": "Troponinas negativas",
                         "dischargeCondition": "Estable", "recommendations": "Evitar esfuerzos", "followUp": "Cardiología en 15 días",
                         "diagnoses": [{"code": "I10X", "role": "PRINCIPAL", "type": "CONFIRMED_NEW"}]}}""";

    @RegisterExtension
    static WireMockExtension patientService = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PatientReferences patients;

    @Autowired
    private JdbcTemplate rootJdbc;

    @Autowired
    private EncounterCommands encounterCommands;

    @Autowired
    private IntegrityQueries integrityQueries;

    private final Staff nurse = new Staff("NURSE");
    private final Staff doctor = new Staff("DOCTOR");
    private final Staff otherDoctor = new Staff("DOCTOR");

    @Autowired
    private Cie10Importer cie10;

    @Autowired
    private S3Client s3;

    @Autowired
    private AttachmentMaintenance maintenance;

    @BeforeEach
    void activeCatalog() {
        if (cie10.releases().stream().noneMatch(TerminologyRelease::active)) {
            UUID admin = UUID.randomUUID();
            cie10.activate(cie10.importWorkbook("cie10.xlsx", Cie10WorkbookFixture.standard(), admin).release().id(), admin);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.patient-service.url", patientService::baseUrl);
        registry.add("clinica.clinical.patient-events.enabled", () -> false);
        registry.add("spring.kafka.admin.auto-create", () -> false);
        ClinicalTestProperties.register(registry);
        MinioTestContainer.register(registry, "api-attachments-staging", "api-attachments");
    }

    @Test
    void emergencyCareFromTriageToDischarge() throws Exception {
        String encounter = openEncounter(nurse, activePatient(), "EMERGENCY");
        joinCareTeam(nurse, encounter, doctor);

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
    void signingNeedsARecentSessionAndLeavesAVerifiableSeal() throws Exception {
        String encounter = openEncounter(nurse, activePatient(), "EMERGENCY");
        String draft = startDraft(nurse, encounter, TRIAGE);

        nurse.performWithSessionFrom(Instant.now().minus(Duration.ofMinutes(20)),
                        post(BASE + "/drafts/" + draft + "/signature").header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RECENT_AUTHENTICATION_REQUIRED"));
        nurse.perform(post(BASE + "/drafts/" + draft + "/signature").header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.email").value("nurse@clinica.test"));

        doctor.perform(get(BASE + "/notes/" + draft + "/signature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.signer.uuid").value(nurse.uuid.toString()))
                .andExpect(jsonPath("$.chain.sequence").value(2))
                .andExpect(jsonPath("$.chain.seal.algorithm").value("SHA256withECDSA"))
                .andExpect(jsonPath("$.chain.seal.keyId").value(TestSealKeys.ACTIVE_KEY_ID));
        mockMvc.perform(get(BASE + "/seal-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].keyId").value(TestSealKeys.ACTIVE_KEY_ID))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void integrityVerificationDetectsTamperingOutsideTheApplication() throws Exception {
        UUID patient = activePatient();
        String encounter = openEncounter(nurse, patient, "EMERGENCY");
        String triage = signedNote(nurse, encounter, TRIAGE);
        String nursing = signedNote(nurse, encounter, """
                {"content": {"type": "NURSING", "observations": "Paciente agitado", "careProvided": "Contención verbal"}}""");
        nurse.perform(post(BASE + "/notes/" + nursing + "/void").content("{\"reason\": \"Paciente equivocado\"}"))
                .andExpect(status().isOk());

        new Staff("MEDICAL_RECORDS").perform(get(BASE + "/patients/" + patient + "/integrity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.chains[0].entries").value(4))
                .andExpect(jsonPath("$.chains[0].head.sequence").value(4))
                .andExpect(jsonPath("$.chains[0].problems", hasSize(0)));
        new Staff("RECEPTIONIST").perform(get(BASE + "/patients/" + patient + "/integrity"))
                .andExpect(status().isForbidden());

        byte[] nursingCiphertext = rootJdbc.queryForObject("SELECT content_ciphertext FROM clinical_ledger.notes WHERE id = ?",
                byte[].class, nursing);
        rootJdbc.update("UPDATE clinical_ledger.notes SET content_ciphertext = ? WHERE id = ?", nursingCiphertext, triage);
        rootJdbc.update("UPDATE clinical_ledger.notes SET author_email = 'otra@clinica.test' WHERE id = ?", nursing);
        rootJdbc.update("DELETE FROM clinical_ledger.note_voids WHERE note_id = ?", nursing);

        new Staff("ADMIN").perform(get(BASE + "/patients/" + patient + "/integrity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.chains[0].problems[*].kind", contains("UNREADABLE_ENTRY", "PAYLOAD_MISMATCH", "MISSING_ENTRY")))
                .andExpect(jsonPath("$.chains[0].problems[0].entryId").value(triage))
                .andExpect(jsonPath("$.chains[0].problems[2].entryType").value("NOTE_VOIDED"));
        doctor.perform(get(BASE + "/notes/" + nursing + "/signature"))
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.problems", contains("PAYLOAD_MISMATCH")));
        nurse.perform(get(BASE + "/notes/" + triage))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("CLINICAL_CONTENT_UNREADABLE"))
                .andExpect(jsonPath("$.detail", not(containsString("Dolor"))));
    }

    @Test
    void careRelationshipGovernsAccessAndEveryDecisionIsAudited() throws Exception {
        UUID patient = activePatient();
        String encounter = openEncounter(nurse, patient, "EMERGENCY");
        Staff outsider = new Staff("DOCTOR");

        outsider.perform(get(BASE + "/encounters/" + encounter))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CARE_RELATIONSHIP_REQUIRED"));
        outsider.perform(get(BASE + "/patients/" + patient + "/encounters"))
                .andExpect(status().isForbidden());
        outsider.perform(post(BASE + "/patients/" + patient + "/emergency-access").content("{\"reason\": \"urgente\"}"))
                .andExpect(status().isBadRequest());
        outsider.perform(post(BASE + "/patients/" + patient + "/emergency-access")
                        .content("{\"reason\": \"Paciente inconsciente trasladado a reanimación\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").exists());
        outsider.perform(get(BASE + "/encounters/" + encounter))
                .andExpect(status().isOk());

        List<JsonNode> events = auditEventsOf(patient);

        assertThat(events).extracting(event -> event.path("action").asText() + ":" + event.path("outcome").asText() + ":"
                        + event.path("basis").asText())
                .containsExactly("OPEN_ENCOUNTER:GRANTED:NEW_ENCOUNTER", "READ_ENCOUNTER:DENIED:null", "LIST_ENCOUNTERS:DENIED:null",
                        "EMERGENCY_ACCESS:GRANTED:EMERGENCY_ACCESS", "READ_ENCOUNTER:GRANTED:EMERGENCY_ACCESS");
        assertThat(events.get(4).path("emergencyReason").asText()).isEqualTo("Paciente inconsciente trasladado a reanimación");
        assertThat(events.get(1).path("actor").path("uuid").asText()).isEqualTo(outsider.uuid.toString());
        assertThat(events).allSatisfy(event -> assertThat(AccessAuditContract.violations(event.toString())).isEmpty());
    }

    @Test
    void restrictedNotesStayWithinTheirEncounterTeam() throws Exception {
        UUID patient = activePatient();
        String counseling = openEncounter(nurse, patient, "OUTPATIENT");
        String note = signedNote(nurse, counseling, """
                {"restriction": "VIOLENCE",
                 "content": {"type": "NURSING", "observations": "Refiere agresión de su pareja", "careProvided": "Ruta de atención activada"}}""");
        openEncounter(doctor, patient, "OUTPATIENT");

        doctor.perform(get(BASE + "/encounters/" + counseling))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes[0].restriction").value("VIOLENCE"))
                .andExpect(jsonPath("$.notes[0].content").doesNotExist());
        doctor.perform(get(BASE + "/notes/" + note))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESTRICTED_NOTE"))
                .andExpect(jsonPath("$.detail", not(containsString("agresión"))));
        nurse.perform(get(BASE + "/notes/" + note))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restriction").value("VIOLENCE"));
        doctor.perform(post(BASE + "/patients/" + patient + "/emergency-access")
                        .content("{\"reason\": \"Politraumatismo, se requieren antecedentes completos\"}"))
                .andExpect(status().isCreated());
        doctor.perform(get(BASE + "/notes/" + note))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.observations").value("Refiere agresión de su pareja"));

        JsonNode lastRead = auditEventsOf(patient).getLast();
        assertThat(lastRead.path("action").asText()).isEqualTo("READ_NOTE");
        assertThat(lastRead.path("restrictedContent").asBoolean()).isTrue();
        assertThat(lastRead.path("basis").asText()).isEqualTo("EMERGENCY_ACCESS");
        assertThat(auditEventsOf(patient)).noneSatisfy(event -> assertThat(event.toString()).contains("agresión"));
    }

    @Test
    void theCareTeamGrowsOnlyFromWithinWhileTheEncounterIsOpen() throws Exception {
        UUID patient = activePatient();
        String encounter = openEncounter(nurse, patient, "OUTPATIENT");
        Staff outsider = new Staff("DOCTOR");

        outsider.perform(post(BASE + "/encounters/" + encounter + "/care-team")
                        .content("{\"clinicianUuid\": \"" + outsider.uuid + "\", \"role\": \"DOCTOR\"}"))
                .andExpect(status().isForbidden());
        nurse.perform(post(BASE + "/encounters/" + encounter + "/care-team")
                        .content("{\"clinicianUuid\": \"" + doctor.uuid + "\", \"role\": \"DOCTOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.added").value(true));
        nurse.perform(post(BASE + "/encounters/" + encounter + "/care-team")
                        .content("{\"clinicianUuid\": \"" + doctor.uuid + "\", \"role\": \"DOCTOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(false));
        signedNote(nurse, encounter, "{\"content\": {\"type\": \"NURSING\", \"observations\": \"Estable\", \"careProvided\": \"Curación\"}}");
        doctor.perform(post(BASE + "/encounters/" + encounter + "/closure")).andExpect(status().isOk());
        doctor.perform(get(BASE + "/patients/" + patient + "/encounters")).andExpect(status().isOk());
        doctor.perform(post(BASE + "/encounters/" + encounter + "/care-team")
                        .content("{\"clinicianUuid\": \"" + outsider.uuid + "\", \"role\": \"DOCTOR\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ENCOUNTER_CLOSED"));
    }

    @Test
    void signedNotesFreezeTheirDiagnosesWithTheCatalogVersion() throws Exception {
        String encounter = openEncounter(doctor, activePatient(), "INPATIENT");

        startDraftRequest(doctor, encounter, """
                {"content": {"type": "PROGRESS", "subjective": "Tos", "diagnoses": [{"code": "Z999", "role": "PRINCIPAL", "type": "IMPRESSION"}]}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_CIE10_CODE"));
        String note = signedNote(doctor, encounter, """
                {"content": {"type": "ADMISSION", "chiefComplaint": "Disnea", "currentIllness": "Tres días de tos", "physicalExam": "Sibilancias",
                             "assessment": "Crisis asmática", "plan": "Broncodilatadores",
                             "diagnoses": [{"code": "J45.9", "role": "PRINCIPAL", "type": "CONFIRMED_REPEATED"},
                                           {"code": "i10x", "role": "RELATED", "type": "CONFIRMED_REPEATED"}]}}""");

        doctor.perform(get(BASE + "/notes/" + note))
                .andExpect(jsonPath("$.content.diagnoses[0].code").value("J459"))
                .andExpect(jsonPath("$.content.diagnoses[0].display").exists())
                .andExpect(jsonPath("$.content.diagnoses[1].catalogVersion").exists());
    }

    @Test
    void signedNotesMaintainListsAndVitalSignsThatStaySealedWithThem() throws Exception {
        UUID patient = activePatient();
        String encounter = openEncounter(doctor, patient, "INPATIENT");
        String now = Instant.now().minusSeconds(60).toString();
        String admission = signedNote(doctor, encounter, """
                {"content": {"type": "ADMISSION", "chiefComplaint": "Disnea", "currentIllness": "Tos", "physicalExam": "Sibilancias",
                             "assessment": "Crisis asmática", "plan": "Salbutamol",
                             "diagnoses": [{"code": "J459", "role": "PRINCIPAL", "type": "CONFIRMED_REPEATED"}]},
                 "updates": [
                   {"kind": "ADD_LIST_ITEM", "details": {"category": "ALLERGY", "substance": "Penicilina", "reaction": "Urticaria", "severity": "SEVERE"}},
                   {"kind": "ADD_LIST_ITEM", "details": {"category": "CHRONIC_CONDITION", "code": "J459", "notes": "Desde la infancia"}},
                   {"kind": "RECORD_VITAL_SIGNS", "measuredAt": "%s",
                    "readings": [{"kind": "RESPIRATORY_RATE", "value": 28}, {"kind": "OXYGEN_SATURATION", "value": 89}]}]}""".formatted(now));

        String lists = doctor.perform(get(BASE + "/patients/" + patient + "/lists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].category").value("ALLERGY"))
                .andExpect(jsonPath("$.items[1].details.display").exists())
                .andExpect(jsonPath("$.items[1].history[0].origin.noteId").value(admission))
                .andReturn().getResponse().getContentAsString();
        String asthma = JsonPath.read(lists, "$.items[1].itemId");
        doctor.perform(get(BASE + "/patients/" + patient + "/vital-signs").param("kind", "OXYGEN_SATURATION"))
                .andExpect(jsonPath("$.observations", hasSize(1)))
                .andExpect(jsonPath("$.observations[0].value").value(89))
                .andExpect(jsonPath("$.observations[0].unit").value("%"));

        startDraftRequest(doctor, encounter, """
                {"content": {"type": "PROGRESS", "subjective": "Mejor"},
                 "updates": [{"kind": "CHANGE_LIST_ITEM_STATUS", "itemId": "%s", "status": "RESOLVED", "reason": "x"}]}""".formatted(UUID.randomUUID()))
                .andExpect(status().isCreated());
        String progress = startDraft(doctor, encounter, """
                {"content": {"type": "PROGRESS", "subjective": "Sin disnea", "objective": "Sin sibilancias", "assessment": "Mejoría", "plan": "Egreso"},
                 "updates": [{"kind": "CHANGE_LIST_ITEM_STATUS", "itemId": "%s", "status": "RESOLVED", "reason": "Controlada"}]}""".formatted(asthma));
        doctor.perform(post(BASE + "/drafts/" + progress + "/signature").header(HttpHeaders.IF_MATCH, "\"0\"")).andExpect(status().isCreated());
        doctor.perform(get(BASE + "/patients/" + patient + "/lists").param("category", "CHRONIC_CONDITION"))
                .andExpect(jsonPath("$.items[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$.items[0].history", hasSize(2)));

        doctor.perform(post(BASE + "/notes/" + progress + "/void").content("{\"reason\": \"Evolución de otro paciente\"}")).andExpect(status().isOk());
        doctor.perform(get(BASE + "/patients/" + patient + "/lists").param("category", "CHRONIC_CONDITION"))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"));

        new Staff("ADMIN").perform(get(BASE + "/patients/" + patient + "/integrity")).andExpect(jsonPath("$.verified").value(true));
        rootJdbc.update("UPDATE clinical_ledger.vital_sign_observations SET value = 97 WHERE note_id = ? AND kind = 'OXYGEN_SATURATION'", admission);
        new Staff("ADMIN").perform(get(BASE + "/patients/" + patient + "/integrity"))
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.chains[0].problems[0].kind").value("PAYLOAD_MISMATCH"))
                .andExpect(jsonPath("$.chains[0].problems[0].entryId").value(admission));
    }

    @Test
    void listItemsFromRestrictedNotesStayHiddenOutsideTheirTeam() throws Exception {
        UUID patient = activePatient();
        String counseling = openEncounter(nurse, patient, "OUTPATIENT");
        signedNote(nurse, counseling, """
                {"restriction": "HIV",
                 "content": {"type": "NURSING", "observations": "Adherencia al tratamiento", "careProvided": "Educación"},
                 "updates": [{"kind": "ADD_LIST_ITEM", "details": {"category": "CURRENT_MEDICATION", "medication": "Antirretroviral", "dose": "1 tableta"}}]}""");
        openEncounter(doctor, patient, "OUTPATIENT");

        doctor.perform(get(BASE + "/patients/" + patient + "/lists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.restrictedItemsHidden").value(1));
        nurse.perform(get(BASE + "/patients/" + patient + "/lists"))
                .andExpect(jsonPath("$.items[0].details.medication").value("Antirretroviral"))
                .andExpect(jsonPath("$.restrictedItemsHidden").value(0));
        assertThat(auditEventsOf(patient).getLast().path("restrictedContent").asBoolean()).isTrue();
    }

    @Test
    void attachmentsTravelSealedWithTheNoteAndDownloadOnlyAfterVerification() throws Exception {
        UUID patient = activePatient();
        String encounter = openEncounter(doctor, patient, "INPATIENT");
        String draft = startDraft(doctor, encounter, """
                {"content": {"type": "PROGRESS", "subjective": "Dolor", "objective": "Murphy positivo", "assessment": "Colecistitis", "plan": "Cirugía"}}""");
        byte[] report = SampleFiles.pdf("Ecografía: vesícula con cálculos");

        doctor.perform(multipart(BASE + "/drafts/" + draft + "/attachments").file(new MockMultipartFile("file", "eco hígado.pdf", "application/pdf", report)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaType").value("application/pdf"))
                .andExpect(jsonPath("$.fileName").value("eco hígado.pdf"))
                .andExpect(jsonPath("$.sha256").value(Attachment.sha256Of(report)));
        doctor.perform(get(BASE + "/drafts/" + draft + "/attachments")).andExpect(jsonPath("$", hasSize(1)));
        String signed = doctor.perform(post(BASE + "/drafts/" + draft + "/signature").header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments[0].size").value(report.length))
                .andReturn().getResponse().getContentAsString();
        String attachment = JsonPath.read(signed, "$.attachments[0].id");

        doctor.perform(get(BASE + "/notes/" + draft + "/attachments/" + attachment))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(report));
        new Staff("DOCTOR").perform(get(BASE + "/notes/" + draft + "/attachments/" + attachment))
                .andExpect(status().isForbidden());
        assertThat(auditEventsOf(patient)).anySatisfy(event -> {
            assertThat(event.path("action").asText()).isEqualTo("READ_ATTACHMENT");
            assertThat(AccessAuditContract.violations(event.toString())).isEmpty();
        });

        new Staff("ADMIN").perform(get(BASE + "/patients/" + patient + "/integrity")).andExpect(jsonPath("$.verified").value(true));
        rootJdbc.update("UPDATE clinical_ledger.note_attachments SET sha256 = REPEAT('a', 64) WHERE id = ?", attachment);
        new Staff("ADMIN").perform(get(BASE + "/patients/" + patient + "/integrity"))
                .andExpect(jsonPath("$.chains[0].problems[0].kind").value("PAYLOAD_MISMATCH"));
        doctor.perform(get(BASE + "/notes/" + draft + "/attachments/" + attachment))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("CLINICAL_CONTENT_UNREADABLE"));
    }

    @Test
    void rejectsUnsafeFilesAndRemovesUploadsThatAreNeverSigned() throws Exception {
        String encounter = openEncounter(nurse, activePatient(), "EMERGENCY");
        String draft = startDraft(nurse, encounter, TRIAGE);

        nurse.perform(multipart(BASE + "/drafts/" + draft + "/attachments")
                        .file(new MockMultipartFile("file", "informe.pdf", "application/pdf", "<html>no soy un pdf</html>".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ATTACHMENT"));
        doctor.perform(multipart(BASE + "/drafts/" + draft + "/attachments").file(new MockMultipartFile("file", "foto.png", "image/png", SampleFiles.png())))
                .andExpect(status().isNotFound());
        String detached = JsonPath.read(nurse.perform(multipart(BASE + "/drafts/" + draft + "/attachments")
                        .file(new MockMultipartFile("file", "herida.png", "image/png", SampleFiles.png())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String discarded = JsonPath.read(nurse.perform(multipart(BASE + "/drafts/" + draft + "/attachments")
                        .file(new MockMultipartFile("file", "herida-2.png", "image/png", SampleFiles.png())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        nurse.perform(delete(BASE + "/drafts/" + draft + "/attachments/" + detached)).andExpect(status().isNoContent());
        nurse.perform(delete(BASE + "/drafts/" + draft).header(HttpHeaders.IF_MATCH, "\"0\"")).andExpect(status().isNoContent());

        for (String staged : List.of(detached, discarded)) {
            assertThatThrownBy(() -> s3.headObject(request -> request.bucket("api-attachments-staging").key("attachments/" + staged)))
                    .isInstanceOf(NoSuchKeyException.class);
        }
    }

    @Test
    void attachmentRetentionFollowsTheLatestCareOfThePatient() throws Exception {
        UUID patient = activePatient();
        String encounter = openEncounter(nurse, patient, "EMERGENCY");
        String draft = startDraft(nurse, encounter, TRIAGE);
        String attachment = JsonPath.read(nurse.perform(multipart(BASE + "/drafts/" + draft + "/attachments")
                        .file(new MockMultipartFile("file", "ekg.pdf", "application/pdf", SampleFiles.pdf("EKG"))))
                .andReturn().getResponse().getContentAsString(), "$.id");
        nurse.perform(post(BASE + "/drafts/" + draft + "/signature").header(HttpHeaders.IF_MATCH, "\"0\"")).andExpect(status().isCreated());
        Instant initial = s3.headObject(request -> request.bucket("api-attachments").key("attachments/" + attachment)).objectLockRetainUntilDate();
        String later = openEncounter(doctor, patient, "OUTPATIENT");
        rootJdbc.update("UPDATE clinical_ledger.encounters SET opened_at = NOW(6) + INTERVAL 2 YEAR WHERE id = ?", later);

        maintenance.extendRetentionForRecentCare();

        Instant extended = s3.headObject(request -> request.bucket("api-attachments").key("attachments/" + attachment)).objectLockRetainUntilDate();
        assertThat(initial).isAfter(Instant.now().plus(Duration.ofDays(365L * 15 - 1)));
        assertThat(extended).isAfter(initial.plus(Duration.ofDays(700)));
    }

    @Test
    void onlySuperAdminsSeeAndRotateTheEncryptionKeys() throws Exception {
        String encounter = openEncounter(nurse, activePatient(), "EMERGENCY");
        signedNote(nurse, encounter, TRIAGE);

        doctor.perform(get(BASE + "/admin/encryption")).andExpect(status().isForbidden());
        new Staff("ADMIN").perform(post(BASE + "/admin/encryption/rewrap")).andExpect(status().isForbidden());
        new Staff("SUPER_ADMIN").perform(get(BASE + "/admin/encryption"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeMasterKeyId").value(TestEncryptionKeys.ACTIVE_KEY_ID))
                .andExpect(jsonPath("$.pendingRewrap").value(0))
                .andExpect(jsonPath("$.retiredKeysCanBeRemoved").value(true));
        new Staff("SUPER_ADMIN").perform(post(BASE + "/admin/encryption/rewrap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rewrapped").value(0));
    }

    @Test
    void concurrentWritesForOnePatientKeepASingleUnbrokenChain() throws Exception {
        UUID patient = activePatient();
        Clinician clinician = new Clinician(doctor.uuid, ClinicalRole.DOCTOR);
        List<Callable<Encounter>> openings = IntStream.range(0, 12)
                .<Callable<Encounter>>mapToObj(i -> () -> encounterCommands.open(patient, EncounterType.OUTPATIENT, null, clinician))
                .toList();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<Encounter> opening : executor.invokeAll(openings)) {
                opening.get();
            }
        }

        assertThat(integrityQueries.verifyPatient(patient)).singleElement().satisfies(chain -> {
            assertThat(chain.verified()).isTrue();
            assertThat(chain.entries()).isEqualTo(12);
        });
    }

    @Test
    void closedEncountersAreClarifiedWithAddendaFromTheSameProfile() throws Exception {
        String encounter = openEncounter(doctor, activePatient(), "OUTPATIENT");
        joinCareTeam(doctor, encounter, nurse);
        joinCareTeam(doctor, encounter, otherDoctor);
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

        joinCareTeam(nurse, encounter, doctor);
        new Staff("NURSE").perform(post(BASE + "/notes/" + note + "/void").content("{\"reason\": \"Otro paciente\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CARE_RELATIONSHIP_REQUIRED"));
        doctor.perform(post(BASE + "/notes/" + note + "/void").content("{\"reason\": \"Otro paciente\"}"))
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

    private void joinCareTeam(Staff member, String encounter, Staff newcomer) throws Exception {
        member.perform(post(BASE + "/encounters/" + encounter + "/care-team")
                        .content("{\"clinicianUuid\": \"" + newcomer.uuid + "\", \"role\": \"" + newcomer.role + "\"}"))
                .andExpect(status().isCreated());
    }

    private List<JsonNode> auditEventsOf(UUID patient) {
        return rootJdbc.queryForList("SELECT payload FROM clinical_outbox.outbox_events WHERE aggregateid = ? ORDER BY created_at, id",
                String.class, patient.toString()).stream().map(ClinicalRecordApiIT::json).toList();
    }

    private static JsonNode json(String value) {
        try {
            return JSON.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
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
            MockHttpServletRequestBuilder authorized = request.header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(role, uuid, issuedAt));
            return mockMvc.perform(request instanceof MockMultipartHttpServletRequestBuilder ? authorized
                    : authorized.contentType(MediaType.APPLICATION_JSON));
        }
    }
}
