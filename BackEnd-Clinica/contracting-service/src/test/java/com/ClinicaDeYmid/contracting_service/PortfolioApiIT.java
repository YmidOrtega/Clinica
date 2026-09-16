package com.ClinicaDeYmid.contracting_service;

import com.ClinicaDeYmid.contracting_service.support.JwtTestTokens;
import com.ClinicaDeYmid.contracting_service.support.MySqlTestContainer;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainer.class)
class PortfolioApiIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
        registry.add("eureka.client.enabled", () -> false);
    }

    @Test
    void offersAServiceAndFindsItByEitherCode() throws Exception {
        String clinicCode = nextClinicCode();
        as("CONTRACTING", post("/api/v1/portfolio-items").content(item(clinicCode, "890201", "Consulta de medicina general")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.categoryLabel").value("Consulta"))
                .andExpect(jsonPath("$.status.offered").value(true));

        as("BILLING", post("/api/v1/portfolio-items/search").content("{\"clinicCode\":\"" + clinicCode.toLowerCase() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].clinicCode").value(clinicCode));

        as("BILLING", post("/api/v1/portfolio-items/search").content("{\"cupsCode\":\"890201\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void demandsExactlyOneSearchCriterion() throws Exception {
        as("BILLING", post("/api/v1/portfolio-items/search").content("{}"))
                .andExpect(status().isBadRequest());
        as("BILLING", post("/api/v1/portfolio-items/search").content("{\"cupsCode\":\"890201\",\"name\":\"Consulta\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loadsTheWholePortfolioWithoutDuplicatingIt() throws Exception {
        String file = "{\"items\":[" + item(nextClinicCode(), "890201", "Consulta de medicina general") + ","
                + item(nextClinicCode(), "903841", "Hemograma IV") + "]}";

        as("CONTRACTING", post("/api/v1/portfolio-items/imports").content(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.unchanged").value(0));

        as("CONTRACTING", post("/api/v1/portfolio-items/imports").content(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.unchanged").value(2));
    }

    @Test
    void rejectsAnEmptyImport() throws Exception {
        as("CONTRACTING", post("/api/v1/portfolio-items/imports").content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retiresAServiceAndBringsItBack() throws Exception {
        String uuid = offer();

        change("CONTRACTING", post("/api/v1/portfolio-items/" + uuid + "/deactivation"), 0,
                "{\"reason\":\"La clínica dejó de habilitar el servicio\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.offered").value(false));

        change("CONTRACTING", post("/api/v1/portfolio-items/" + uuid + "/reactivation"), 1, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("ACTIVE"));
    }

    @Test
    void demandsTheVersionToCorrectAService() throws Exception {
        String uuid = offer();

        as("CONTRACTING", put("/api/v1/portfolio-items/" + uuid).content(item("OTRO-1", "890301", "Consulta de pediatría")))
                .andExpect(status().isPreconditionRequired());
    }

    @ParameterizedTest
    @ValueSource(strings = {"BILLING", "RECEPTIONIST"})
    void onlyContractingAndAdministratorsChangeThePortfolio(String role) throws Exception {
        as(role, post("/api/v1/portfolio-items").content(item(nextClinicCode(), "890201", "Consulta de medicina general")))
                .andExpect(status().isForbidden());
    }

    private String offer() throws Exception {
        String body = as("CONTRACTING", post("/api/v1/portfolio-items")
                .content(item(nextClinicCode(), "890201", "Consulta de medicina general")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.uuid");
    }

    private ResultActions as(String role, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role)));
    }

    private ResultActions change(String role, MockHttpServletRequestBuilder request, long version, String body) throws Exception {
        MockHttpServletRequestBuilder prepared = request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.IF_MATCH, "\"" + version + "\"")
                .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role));
        return mockMvc.perform(body == null ? prepared.content("{}") : prepared.content(body));
    }

    private static String nextClinicCode() {
        return String.format("SRV-%04d", SEQUENCE.incrementAndGet());
    }

    private static String item(String clinicCode, String cupsCode, String name) {
        return "{\"cupsCode\":\"" + cupsCode + "\",\"clinicCode\":\"" + clinicCode + "\",\"name\":\"" + name
                + "\",\"category\":\"" + (cupsCode.startsWith("90") ? "LABORATORY" : "CONSULTATION") + "\"}";
    }
}
