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
class SpecialtyApiIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
    }

    @Test
    void registersASpecialtyWithItsSubSpecialtyAndReadsItBack() throws Exception {
        String code = nextCode();
        String created = as("HUMAN_RESOURCES", post("/api/v1/specialties")
                .content("{\"code\":\"" + code.toLowerCase() + "\",\"name\":\"Cardiología\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.status.active").value(true))
                .andReturn().getResponse().getContentAsString();
        String uuid = JsonPath.read(created, "$.uuid");

        as("HUMAN_RESOURCES", post("/api/v1/specialties/" + uuid + "/sub-specialties")
                .content("{\"code\":\"" + nextCode() + "\",\"name\":\"Hemodinamia\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.specialtyCode").value(code));

        as("ADMIN", get("/api/v1/specialties/" + uuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subSpecialties.length()").value(1));
    }

    @Test
    void protectsEveryChangeWithTheVersion() throws Exception {
        String uuid = registerSpecialty();

        as("HUMAN_RESOURCES", put("/api/v1/specialties/" + uuid).content("{\"name\":\"Cardiología clínica\"}"))
                .andExpect(status().isPreconditionRequired());

        as("HUMAN_RESOURCES", put("/api/v1/specialties/" + uuid)
                .header(HttpHeaders.IF_MATCH, "\"7\"").content("{\"name\":\"Cardiología clínica\"}"))
                .andExpect(status().isPreconditionFailed());

        as("HUMAN_RESOURCES", put("/api/v1/specialties/" + uuid)
                .header(HttpHeaders.IF_MATCH, "\"0\"").content("{\"name\":\"Cardiología clínica\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.name").value("Cardiología clínica"));
    }

    @Test
    void closesAndReopensASpecialty() throws Exception {
        String uuid = registerSpecialty();

        as("HUMAN_RESOURCES", post("/api/v1/specialties/" + uuid + "/deactivation")
                .header(HttpHeaders.IF_MATCH, "\"0\"").content("{\"reason\":\"corta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRACTITIONERS_INVALID_DATA"));

        as("HUMAN_RESOURCES", post("/api/v1/specialties/" + uuid + "/deactivation")
                .header(HttpHeaders.IF_MATCH, "\"0\"").content("{\"reason\":\"La clínica cerró el servicio\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.active").value(false));

        as("HUMAN_RESOURCES", post("/api/v1/specialties/" + uuid + "/reactivation")
                .header(HttpHeaders.IF_MATCH, "\"1\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.active").value(true));
    }

    @Test
    void loadsTheCatalogueInOneCallAndRepeatsItWithoutChanges() throws Exception {
        String body = "{\"specialties\":[{\"code\":\"" + nextCode() + "\",\"name\":\"Pediatría\","
                + "\"subSpecialties\":[{\"code\":\"" + nextCode() + "\",\"name\":\"Neonatología\"}]}]}";

        as("HUMAN_RESOURCES", post("/api/v1/specialties/imports").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.subSpecialtiesCreated").value(1))
                .andExpect(jsonPath("$.changedSomething").value(true));

        as("HUMAN_RESOURCES", post("/api/v1/specialties/imports").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unchanged").value(1))
                .andExpect(jsonPath("$.changedSomething").value(false));

        as("HUMAN_RESOURCES", post("/api/v1/specialties/imports").content("{\"specialties\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTalentManagementWritesTheCatalogue() throws Exception {
        String uuid = registerSpecialty();

        as("DOCTOR", post("/api/v1/specialties").content("{\"code\":\"" + nextCode() + "\",\"name\":\"Cardiología\"}"))
                .andExpect(status().isForbidden());

        as("RECEPTIONIST", get("/api/v1/specialties/" + uuid))
                .andExpect(status().isForbidden());

        as("DOCTOR", get("/api/v1/specialties/" + uuid))
                .andExpect(status().isForbidden());

        as("ADMIN", get("/api/v1/specialties?status=ACTIVE"))
                .andExpect(status().isOk());
    }

    private String registerSpecialty() throws Exception {
        String created = as("HUMAN_RESOURCES", post("/api/v1/specialties")
                .content("{\"code\":\"" + nextCode() + "\",\"name\":\"Cardiología\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.uuid");
    }

    private ResultActions as(String role, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role))
                .contentType(MediaType.APPLICATION_JSON));
    }

    private static String nextCode() {
        return "API" + SEQUENCE.incrementAndGet() + "Z";
    }
}
