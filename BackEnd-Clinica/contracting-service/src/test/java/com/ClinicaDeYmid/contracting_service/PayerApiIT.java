package com.ClinicaDeYmid.contracting_service;

import com.ClinicaDeYmid.commons.security.testing.SecurityTestTokens;
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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainer.class)
class PayerApiIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(100);

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
        registry.add("eureka.client.enabled", () -> false);
    }

    @Test
    void registersAPayerAndReturnsItsVersion() throws Exception {
        as("CONTRACTING", post("/api/v1/payers").content(registration(nextNit())))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/v1/payers/")))
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.status.code").value("ACTIVE"))
                .andExpect(jsonPath("$.status.contractable").value(true))
                .andExpect(jsonPath("$.typeLabel").value("Entidad Promotora de Salud"));
    }

    @Test
    void rejectsASecondPayerWithTheSameNit() throws Exception {
        String nit = nextNit();
        as("CONTRACTING", post("/api/v1/payers").content(registration(nit))).andExpect(status().isCreated());

        as("CONTRACTING", post("/api/v1/payers").content(registration(nit)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYER_NIT_ALREADY_REGISTERED"));
    }

    @Test
    void rejectsANitWithAWrongVerificationDigit() throws Exception {
        as("CONTRACTING", post("/api/v1/payers").content(registration("890903938-9")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACTING_INVALID_DATA"));
    }

    @Test
    void demandsTheVersionBeforeChangingAPayer() throws Exception {
        String uuid = register();

        as("CONTRACTING", put("/api/v1/payers/" + uuid + "/contact").content(contact()))
                .andExpect(status().isPreconditionRequired());

        mockMvc.perform(put("/api/v1/payers/" + uuid + "/contact").content(contact())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.IF_MATCH, "\"7\"")
                        .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer("CONTRACTING")))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void walksThroughTheLifeOfAPayer() throws Exception {
        String uuid = register();

        change("CONTRACTING", post("/api/v1/payers/" + uuid + "/suspension"), 0, "{\"reason\":\"Cartera vencida sin acuerdo de pago\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("SUSPENDED"))
                .andExpect(jsonPath("$.status.contractable").value(false));

        change("CONTRACTING", post("/api/v1/payers/" + uuid + "/reactivation"), 1, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("ACTIVE"));

        change("CONTRACTING", post("/api/v1/payers/" + uuid + "/deactivation"), 2, "{\"reason\":\"Liquidación de la entidad\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("DEACTIVATED"));

        as("ADMIN", get("/api/v1/payers/" + uuid + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].changeType").value("CREATED"))
                .andExpect(jsonPath("$[3].state.status.code").value("DEACTIVATED"))
                .andExpect(jsonPath("$[3].revisedBy").value(JwtTestTokens.USERS.get("CONTRACTING")));
    }

    @Test
    void findsPayersByNitAndBySocialReason() throws Exception {
        String nit = nextNit();
        as("CONTRACTING", post("/api/v1/payers").content(registration(nit))).andExpect(status().isCreated());

        as("BILLING", post("/api/v1/payers/search").content("{\"nit\":\"" + nit + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nit").value(containsString(nit.substring(0, 9))));

        as("RECEPTIONIST", post("/api/v1/payers/search").content("{\"socialReason\":\"Salud\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        as("BILLING", post("/api/v1/payers/search").content("{\"nit\":\"" + nit + "\",\"socialReason\":\"Salud\"}"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"BILLING", "RECEPTIONIST", "DOCTOR"})
    void onlyContractingAndAdministratorsChangePayers(String role) throws Exception {
        as(role, post("/api/v1/payers").content(registration(nextNit())))
                .andExpect(status().isForbidden());
    }

    @Test
    void refusesCallersWithoutAnyPermission() throws Exception {
        as("DOCTOR", post("/api/v1/payers/search").content("{\"socialReason\":\"Salud\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/payers/search").content("{\"socialReason\":\"Salud\"}").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesAServiceTokenThatCarriesARoleClaim() throws Exception {
        mockMvc.perform(post("/api/v1/payers").content(registration(nextNit())).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, SecurityTestTokens.service("billing-service").claim("role", "ADMIN").bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptsTokensIssuedForThisServiceOnly() throws Exception {
        UUID contracting = UUID.fromString(JwtTestTokens.USERS.get("CONTRACTING"));

        mockMvc.perform(post("/api/v1/payers/search").content("{\"socialReason\":\"Salud\"}").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, SecurityTestTokens.staff("CONTRACTING", contracting)
                                .audience("contracting-service").bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/payers/search").content("{\"socialReason\":\"Salud\"}").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, SecurityTestTokens.staff("CONTRACTING", contracting)
                                .audience("billing-service").bearer()))
                .andExpect(status().isUnauthorized());
    }

    private String register() throws Exception {
        String body = as("CONTRACTING", post("/api/v1/payers").content(registration(nextNit())))
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

    private static String nextNit() {
        return String.format("9%08d", SEQUENCE.incrementAndGet());
    }

    private static String registration(String nit) {
        return "{\"identity\":{\"socialReason\":\"Salud Total EPS S.A.\",\"nit\":\"" + nit + "\",\"type\":\"EPS\",\"adresCode\":\"EPS002\"},"
                + "\"contact\":{\"address\":\"Calle 100 # 7-33\",\"phone\":\"6017429000\",\"billingEmail\":\"radicacion@saludtotal.test\"}}";
    }

    private static String contact() {
        return "{\"address\":\"Carrera 7 # 71-21\",\"phone\":\"6013456789\",\"billingEmail\":null}";
    }
}
