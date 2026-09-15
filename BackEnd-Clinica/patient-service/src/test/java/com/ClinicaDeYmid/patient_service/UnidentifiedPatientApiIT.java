package com.ClinicaDeYmid.patient_service;

import com.ClinicaDeYmid.patient_service.support.JwtTestTokens;
import com.ClinicaDeYmid.patient_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.patient_service.support.PatientJson;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
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

import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainer.class)
class UnidentifiedPatientApiIT {

    private static final String BASE = "/api/v1/unidentified-patients";
    private static final String ARRIVAL = """
            {"sex": "MALE", "estimatedBirthYear": 1980, "description": "Hombre adulto inconsciente, camisa azul, cicatriz en la frente"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
        registry.add("eureka.client.enabled", () -> false);
    }

    @Test
    void emergencyStaffRegisterUnidentifiedPatientsWithAWristbandCode() throws Exception {
        as("NURSE", post(BASE).content(ARRIVAL))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.code", matchesPattern("^NN-\\d{4}-\\d{6}$")))
                .andExpect(jsonPath("$.status.code").value("UNIDENTIFIED"))
                .andExpect(jsonPath("$.audit.createdBy").value(JwtTestTokens.USERS.get("NURSE")));
    }

    @Test
    void validatesTheArrivalData() throws Exception {
        as("DOCTOR", post(BASE).content("{\"sex\": \"MALE\", \"description\": \"Hombre\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PATIENT_INVALID_DATA"));
    }

    @Test
    void onlyRecordsStaffAndAdministratorsCanIdentify() throws Exception {
        String uuid = registerUnidentified();
        String patientUuid = registerPatient();

        as("NURSE", identification(uuid, 0, "{\"patientUuid\": \"" + patientUuid + "\", \"reason\": \"Cédula\"}"))
                .andExpect(status().isForbidden());

        as("MEDICAL_RECORDS", identification(uuid, 0, "{\"patientUuid\": \"" + patientUuid + "\", \"reason\": \"Familiar presentó la cédula\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("IDENTIFIED"))
                .andExpect(jsonPath("$.status.identifiedPatientUuid").value(patientUuid))
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""));
    }

    @Test
    void registersTheRealPatientWhileIdentifying() throws Exception {
        String uuid = registerUnidentified();
        String document = PatientJson.uniqueCedula();
        String body = "{\"registration\": " + PatientJson.uninsuredRegistration(document) + ", \"reason\": \"Recuperó la conciencia\"}";

        String identified = as("ADMIN", identification(uuid, 0, body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String patientUuid = JsonPath.read(identified, "$.status.identifiedPatientUuid");
        as("DOCTOR", get("/api/v1/patients/" + patientUuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.number").value(document));
    }

    @Test
    void requiresExactlyOneIdentificationModeAndTheCurrentVersion() throws Exception {
        String uuid = registerUnidentified();

        as("ADMIN", identification(uuid, 0, "{\"reason\": \"Cédula\"}"))
                .andExpect(status().isBadRequest());
        as("ADMIN", post(BASE + "/" + uuid + "/identification").content("{\"patientUuid\": \"" + UUID.randomUUID() + "\", \"reason\": \"x\"}"))
                .andExpect(status().isPreconditionRequired());
        as("ADMIN", identification(uuid, 0, "{\"patientUuid\": \"" + UUID.randomUUID() + "\", \"reason\": \"Cédula\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
    }

    @Test
    void revertsMistakenIdentificationsAndRecordsDeaths() throws Exception {
        String uuid = registerUnidentified();
        String patientUuid = registerPatient();
        as("ADMIN", identification(uuid, 0, "{\"patientUuid\": \"" + patientUuid + "\", \"reason\": \"Parecido físico\"}"))
                .andExpect(status().isOk());

        as("MEDICAL_RECORDS", post(BASE + "/" + uuid + "/identification-reversal").header(HttpHeaders.IF_MATCH, "\"1\"")
                .content("{\"reason\": \"La familia confirmó que no es la persona\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("UNIDENTIFIED"))
                .andExpect(jsonPath("$.status.reason").value("La familia confirmó que no es la persona"));

        as("DOCTOR", post(BASE + "/" + uuid + "/death").header(HttpHeaders.IF_MATCH, "\"2\"").content("{\"dateOfDeath\": \"2026-09-13\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("DECEASED"));

        as("ADMIN", identification(uuid, 3, "{\"patientUuid\": \"" + patientUuid + "\", \"reason\": \"Cédula\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNIDENTIFIED_PATIENT_INVALID_STATUS_TRANSITION"));
    }

    @Test
    void reportsUnknownUnidentifiedPatients() throws Exception {
        as("RECEPTIONIST", get(BASE + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UNIDENTIFIED_PATIENT_NOT_FOUND"));
    }

    private String registerUnidentified() throws Exception {
        String body = as("RECEPTIONIST", post(BASE).content(ARRIVAL)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.uuid");
    }

    private String registerPatient() throws Exception {
        String body = as("ADMIN", post("/api/v1/patients").content(PatientJson.uninsuredRegistration(PatientJson.uniqueCedula())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.uuid");
    }

    private static MockHttpServletRequestBuilder identification(String uuid, long version, String body) {
        return post(BASE + "/" + uuid + "/identification").header(HttpHeaders.IF_MATCH, "\"" + version + "\"").content(body);
    }

    private ResultActions as(String role, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request
                .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role))
                .contentType(MediaType.APPLICATION_JSON));
    }
}
