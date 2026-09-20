package com.ClinicaDeYmid.practitioners_service;

import com.ClinicaDeYmid.practitioners_service.support.JwtTestTokens;
import com.ClinicaDeYmid.practitioners_service.support.MySqlTestContainer;
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

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainer.class)
class PractitionerApiIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
    }

    @Test
    void registersAPractitionerAndFindsItByDocumentWithoutPuttingItInTheUrl() throws Exception {
        String document = nextDocument();
        String uuid = register(document);

        as("HUMAN_RESOURCES", post("/api/v1/practitioners/search")
                .content("{\"document\":{\"type\":\"CEDULA_DE_CIUDADANIA\",\"number\":\"" + document + "\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].uuid").value(uuid))
                .andExpect(jsonPath("$[0].document.label").value("Cédula de Ciudadanía"));

        as("HUMAN_RESOURCES", get("/api/v1/practitioners/" + uuid))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.status.attends").value(true))
                .andExpect(jsonPath("$.relationshipLabel").value("Planta"));
    }

    @Test
    void refusesARepeatedDocumentRegistrationOrEmail() throws Exception {
        String document = nextDocument();
        register(document);

        as("HUMAN_RESOURCES", post("/api/v1/practitioners").content(body(document, nextRegistration(), nextEmail())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRACTITIONER_DOCUMENT_ALREADY_REGISTERED"));
    }

    @Test
    void correctsTheRecordOnlyWithTheRightVersion() throws Exception {
        String uuid = register(nextDocument());

        as("HUMAN_RESOURCES", put("/api/v1/practitioners/" + uuid + "/contact")
                .content("{\"email\":\"" + nextEmail() + "\",\"mobile\":\"3009998877\"}"))
                .andExpect(status().isPreconditionRequired());

        as("HUMAN_RESOURCES", put("/api/v1/practitioners/" + uuid + "/contact")
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .content("{\"email\":\"" + nextEmail() + "\",\"mobile\":\"3009998877\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.contact.mobile").value("3009998877"));
    }

    @Test
    void assignsSpecialtiesFromTheCatalogue() throws Exception {
        String specialtyCode = nextCode();
        String specialtyUuid = JsonPath.read(as("HUMAN_RESOURCES", post("/api/v1/specialties")
                .content("{\"code\":\"" + specialtyCode + "\",\"name\":\"Cardiología\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.uuid");
        String subCode = nextCode();
        as("HUMAN_RESOURCES", post("/api/v1/specialties/" + specialtyUuid + "/sub-specialties")
                .content("{\"code\":\"" + subCode + "\",\"name\":\"Hemodinamia\"}"))
                .andExpect(status().isCreated());

        String uuid = register(nextDocument());

        String assigned = as("HUMAN_RESOURCES", put("/api/v1/practitioners/" + uuid + "/specialties")
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .content("{\"specialties\":[{\"specialtyCode\":\"" + specialtyCode + "\",\"subSpecialtyCode\":\""
                        + subCode + "\",\"principal\":true}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialties.length()").value(1))
                .andExpect(jsonPath("$.specialties[0].subSpecialtyName").value("Hemodinamia"))
                .andExpect(jsonPath("$.specialties[0].principal").value(true))
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        as("HUMAN_RESOURCES", post("/api/v1/practitioners/search")
                .content("{\"specialtyCode\":\"" + specialtyCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].uuid").value(uuid));

        as("HUMAN_RESOURCES", put("/api/v1/practitioners/" + uuid + "/specialties")
                .header(HttpHeaders.IF_MATCH, assigned)
                .content("{\"specialties\":[{\"specialtyCode\":\"" + specialtyCode + "\",\"principal\":false}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRINCIPAL_SPECIALTY_REQUIRED"));
    }

    @Test
    void suspendsRetiresAndKeepsTheHistory() throws Exception {
        String uuid = register(nextDocument());

        as("HUMAN_RESOURCES", post("/api/v1/practitioners/" + uuid + "/suspension")
                .header(HttpHeaders.IF_MATCH, "\"0\"").content("{\"reason\":\"Investigación disciplinaria\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("SUSPENDED"));

        as("HUMAN_RESOURCES", post("/api/v1/practitioners/" + uuid + "/retirement")
                .header(HttpHeaders.IF_MATCH, "\"1\"").content("{\"reason\":\"Terminó su contrato\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("RETIRED"));

        as("HUMAN_RESOURCES", get("/api/v1/practitioners/" + uuid + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].changeType").value("CREATED"))
                .andExpect(jsonPath("$[2].state.status.code").value("RETIRED"))
                .andExpect(jsonPath("$[2].revisedBy").value(JwtTestTokens.USERS.get("HUMAN_RESOURCES")));
    }

    @Test
    void onlyTalentManagementWritesTheDirectory() throws Exception {
        String uuid = register(nextDocument());

        as("DOCTOR", get("/api/v1/practitioners/" + uuid)).andExpect(status().isForbidden());
        as("RECEPTIONIST", post("/api/v1/practitioners").content(body(nextDocument(), nextRegistration(), nextEmail())))
                .andExpect(status().isForbidden());
        as("ADMIN", get("/api/v1/practitioners/" + uuid)).andExpect(status().isOk());
    }

    private String register(String document) throws Exception {
        String created = as("HUMAN_RESOURCES", post("/api/v1/practitioners")
                .content(body(document, nextRegistration(), nextEmail())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.uuid");
    }

    private static String body(String document, String registration, String email) {
        return "{\"document\":{\"type\":\"CEDULA_DE_CIUDADANIA\",\"number\":\"" + document + "\"},"
                + "\"firstNames\":\"Ana María\",\"lastNames\":\"Restrepo Gómez\","
                + "\"registration\":{\"number\":\"" + registration + "\",\"registeredOn\":\"2015-03-01\"},"
                + "\"contact\":{\"email\":\"" + email + "\",\"mobile\":\"3001234567\"},"
                + "\"relationship\":\"STAFF\"}";
    }

    private ResultActions as(String role, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role))
                .contentType(MediaType.APPLICATION_JSON));
    }

    private static String nextDocument() {
        return "10" + (7000000 + SEQUENCE.incrementAndGet());
    }

    private static String nextRegistration() {
        return "RM-" + (10000 + SEQUENCE.incrementAndGet());
    }

    private static String nextEmail() {
        return "profesional" + SEQUENCE.incrementAndGet() + "@clinica.local";
    }

    private static String nextCode() {
        return "PRA" + SEQUENCE.incrementAndGet() + "Y";
    }
}
