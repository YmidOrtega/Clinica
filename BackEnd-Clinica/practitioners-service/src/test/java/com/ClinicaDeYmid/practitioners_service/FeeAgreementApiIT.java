package com.ClinicaDeYmid.practitioners_service;

import com.ClinicaDeYmid.commons.security.testing.SecurityTestTokens;
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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainer.class)
class FeeAgreementApiIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
    }

    @Test
    void agreesHourlyFeesAndRevokesThemWithTheNextAgreement() throws Exception {
        String uuid = register();

        String first = as("HUMAN_RESOURCES", post(path(uuid))
                .content("{\"basis\":\"HOURLY\",\"amount\":85000.00,\"validFrom\":\"2026-01-01\","
                        + "\"note\":\"Turno de consulta externa\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.basisLabel").value("Por hora"))
                .andExpect(jsonPath("$.inForce").value(true))
                .andExpect(jsonPath("$.agreedBy").value(JwtTestTokens.USERS.get("HUMAN_RESOURCES")))
                .andReturn().getResponse().getContentAsString();

        as("HUMAN_RESOURCES", post(path(uuid))
                .content("{\"basis\":\"PER_SHIFT\",\"amount\":420000.00,\"validFrom\":\"2026-07-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.basis").value("PER_SHIFT"));

        as("HUMAN_RESOURCES", get(path(uuid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].validFrom").value("2026-07-01"))
                .andExpect(jsonPath("$[1].uuid").value(JsonPath.read(first, "$.uuid").toString()))
                .andExpect(jsonPath("$[1].revokedOn").value("2026-07-01"));

        as("HUMAN_RESOURCES", get(path(uuid) + "/in-force?on=2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basis").value("HOURLY"))
                .andExpect(jsonPath("$.amount").value(85000.00));

        as("HUMAN_RESOURCES", get(path(uuid) + "/in-force?on=2025-12-31"))
                .andExpect(status().isNoContent());
    }

    @Test
    void agreesFeesPerProcedureWithItsLines() throws Exception {
        String uuid = register();

        as("HUMAN_RESOURCES", post(path(uuid))
                .content("{\"basis\":\"PER_PROCEDURE\",\"validFrom\":\"2026-01-01\",\"procedures\":["
                        + "{\"serviceCode\":\"890201\",\"amount\":45000.00},"
                        + "{\"serviceCode\":\"903841\",\"amount\":18000.00}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").doesNotExist())
                .andExpect(jsonPath("$.procedures.length()").value(2))
                .andExpect(jsonPath("$.procedures[0].serviceCode").value("890201"));

        as("HUMAN_RESOURCES", post(path(uuid))
                .content("{\"basis\":\"PER_PROCEDURE\",\"validFrom\":\"2026-02-01\",\"amount\":50000.00,"
                        + "\"procedures\":[{\"serviceCode\":\"890201\",\"amount\":45000.00}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRACTITIONERS_INVALID_DATA"));

        as("HUMAN_RESOURCES", post(path(uuid))
                .content("{\"basis\":\"HOURLY\",\"amount\":85000.00,\"validFrom\":\"2026-01-01\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FEE_AGREEMENT_OVERLAPS"));
    }

    @Test
    void demandsARecentSecondFactorAndTheFeesPermission() throws Exception {
        String uuid = register();
        String body = "{\"basis\":\"HOURLY\",\"amount\":85000.00,\"validFrom\":\"2026-01-01\"}";

        mockMvc.perform(post(path(uuid)).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(HttpHeaders.AUTHORIZATION, staleSecondFactor()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));

        as("ADMIN", post(path(uuid)).content(body)).andExpect(status().isCreated());
    }

    @Test
    void refusesFeesForARetiredPractitioner() throws Exception {
        String uuid = register();
        as("HUMAN_RESOURCES", post("/api/v1/practitioners/" + uuid + "/retirement")
                .header(HttpHeaders.IF_MATCH, "\"0\"").content("{\"reason\":\"Terminó su contrato\"}"))
                .andExpect(status().isOk());

        as("HUMAN_RESOURCES", post(path(uuid))
                .content("{\"basis\":\"HOURLY\",\"amount\":85000.00,\"validFrom\":\"2026-01-01\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FEES_FOR_RETIRED_PRACTITIONER"));
    }

    private static String path(String practitionerUuid) {
        return "/api/v1/practitioners/" + practitionerUuid + "/fee-agreements";
    }

    private static String staleSecondFactor() {
        return SecurityTestTokens.staff("HUMAN_RESOURCES", UUID.fromString(JwtTestTokens.USERS.get("HUMAN_RESOURCES")))
                .authenticatedAt(Instant.now().minus(Duration.ofHours(1)))
                .bearer();
    }

    private String register() throws Exception {
        int sequence = SEQUENCE.incrementAndGet();
        String created = as("HUMAN_RESOURCES", post("/api/v1/practitioners")
                .content("{\"document\":{\"type\":\"CEDULA_DE_CIUDADANIA\",\"number\":\"30" + (6000000 + sequence) + "\"},"
                        + "\"firstNames\":\"Laura\",\"lastNames\":\"Gómez Ruiz\","
                        + "\"registration\":{\"number\":\"RF-" + (30000 + sequence) + "\"},"
                        + "\"contact\":{\"email\":\"honorarios" + sequence + "@clinica.local\",\"mobile\":\"3007778899\"},"
                        + "\"relationship\":\"CONTRACTOR\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.uuid");
    }

    private ResultActions as(String role, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role))
                .contentType(MediaType.APPLICATION_JSON));
    }
}
