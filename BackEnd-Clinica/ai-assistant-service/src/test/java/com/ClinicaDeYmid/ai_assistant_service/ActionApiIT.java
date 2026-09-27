package com.ClinicaDeYmid.ai_assistant_service;

import com.ClinicaDeYmid.ai_assistant_service.service.ActionService;
import com.ClinicaDeYmid.ai_assistant_service.service.ConversationService;
import com.ClinicaDeYmid.ai_assistant_service.service.InvoiceProjection;
import com.ClinicaDeYmid.ai_assistant_service.service.InvoiceState;
import com.ClinicaDeYmid.ai_assistant_service.shared.ActionKind;
import com.ClinicaDeYmid.ai_assistant_service.support.JwtTestTokens;
import com.ClinicaDeYmid.ai_assistant_service.support.ModelSimulator;
import com.ClinicaDeYmid.ai_assistant_service.support.PostgresTestContainer;
import com.ClinicaDeYmid.ai_assistant_service.support.StubbedServices;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class ActionApiIT {

    private static final String ACTIONS = "/api/v1/assistant/actions";
    private static final UUID BILLER = UUID.fromString(JwtTestTokens.USERS.get("BILLING"));
    private static final AtomicInteger SEQUENCE = new AtomicInteger(800);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoiceProjection projection;

    @Autowired
    private ConversationService conversations;

    @Autowired
    private ActionService actions;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
        registry.add("spring.ai.openai.base-url", StubbedServices::baseUrl);
        registry.add("spring.cloud.openfeign.client.config.billing-service.url", StubbedServices::baseUrl);
    }

    @BeforeEach
    void freshStubs() {
        StubbedServices.server().resetAll();
    }

    @Test
    void theModelOnlyProposesAndTheUserConfirmsOnce() throws Exception {
        UUID invoice = UUID.randomUUID();
        String number = issued(invoice);
        String conversation = conversations.start(BILLER, "Rechazos").uuid().toString();
        ModelSimulator.callsATool("proposeAction",
                "{\"kind\":\"SEND_TO_DIAN\",\"invoiceNumber\":\"" + number + "\",\"reason\":\"La DIAN no respondió\"}",
                "Te dejé propuesto el reenvío; confírmalo para ejecutarlo.");

        String action = JsonPath.read(as("BILLING", org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/assistant/conversations/" + conversation + "/messages")
                        .content("{\"content\":\"Reenvíala a la DIAN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.proposedActions[0].kind").value("SEND_TO_DIAN"))
                .andExpect(jsonPath("$.proposedActions[0].status").value("PROPOSED"))
                .andReturn().getResponse().getContentAsString(), "$.proposedActions[0].uuid");
        StubbedServices.server().verify(0, postRequestedFor(urlPathEqualTo(billing(invoice, "dian-delivery"))));

        StubbedServices.server().stubFor(post(urlPathEqualTo(billing(invoice, "dian-delivery")))
                .willReturn(okJson("{\"number\":\"" + number + "\",\"dian\":{\"status\":\"AWAITING_VALIDATION\"}}")));
        as("BILLING", confirmation(action)).andExpect(status().isPreconditionRequired());
        as("BILLING", confirmation(action).header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.outcome").value("Enviada a la DIAN; estado AWAITING_VALIDATION"));
        as("BILLING", confirmation(action).header(HttpHeaders.IF_MATCH, "\"2\""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACTION_NOT_PENDING"));
        StubbedServices.server().verify(1, postRequestedFor(urlPathEqualTo(billing(invoice, "dian-delivery"))));
    }

    @Test
    void whatBillingRefusesIsRecordedAsFailedWithItsReason() throws Exception {
        UUID invoice = UUID.randomUUID();
        String number = issued(invoice);
        UUID conversation = conversations.start(BILLER, "RIPS").uuid();
        String rips = actions.propose(BILLER, conversation, number, ActionKind.VALIDATE_RIPS, "Sin CUV").uuid().toString();
        String sign = actions.propose(BILLER, conversation, number, ActionKind.SIGN, "Sin firma").uuid().toString();
        StubbedServices.server().stubFor(post(urlPathEqualTo(billing(invoice, "rips-validation"))).willReturn(aResponse()
                .withStatus(422).withHeader("Content-Type", "application/problem+json")
                .withBody("{\"code\":\"RIPS_INCOMPLETE\",\"detail\":\"El RIPS tiene vacíos\"}")));
        StubbedServices.server().stubFor(post(urlPathEqualTo(billing(invoice, "signature")))
                .willReturn(aResponse().withStatus(403)));

        as("BILLING", confirmation(rips).header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.outcome").value("billing la rechazó (422): RIPS_INCOMPLETE El RIPS tiene vacíos"));
        as("BILLING", confirmation(sign).header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.outcome").value("el usuario no tiene permiso en billing para hacerlo"));
    }

    @Test
    void onlyItsOwnerDecidesAProposalAndADiscardedOneNeverRuns() throws Exception {
        UUID invoice = UUID.randomUUID();
        String number = issued(invoice);
        UUID conversation = conversations.start(BILLER, "Firma").uuid();
        String action = actions.propose(BILLER, conversation, number, ActionKind.SIGN, "Sin firma").uuid().toString();

        as("ACCOUNTS_RECEIVABLE", confirmation(action).header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isNotFound());
        as("BILLING", org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(ACTIONS + "/" + action + "/discard").header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(jsonPath("$.status").value("DISCARDED"));
        as("BILLING", confirmation(action).header(HttpHeaders.IF_MATCH, "\"1\""))
                .andExpect(status().isUnprocessableEntity());
        as("BILLING", get(ACTIONS + "?status=DISCARDED"))
                .andExpect(jsonPath("$[?(@.uuid == '" + action + "')].status").value(
                        org.hamcrest.Matchers.contains("DISCARDED")));
        StubbedServices.server().verify(0, postRequestedFor(urlPathEqualTo(billing(invoice, "signature"))));
    }

    private static MockHttpServletRequestBuilder confirmation(String action) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(ACTIONS + "/" + action + "/confirmation");
    }

    private static String billing(UUID invoice, String action) {
        return "/api/v1/billing/invoices/" + invoice + "/" + action;
    }

    private String issued(UUID invoice) {
        String number = "SETP99200" + SEQUENCE.incrementAndGet();
        projection.follow(new InvoiceState(invoice, "InvoiceIssued", Instant.now(), number, "SERVICES", "ISSUED",
                LocalDate.of(2026, 9, 1), "ADM-2026-000900", "PAYER", "900156264", "CT-1", null,
                new BigDecimal("45000.00"), BigDecimal.ZERO, new BigDecimal("45000.00"), null, null, null, null,
                null, null, "{\"creditNotes\":[],\"objections\":[]}"));
        return number;
    }

    private ResultActions as(String role, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role)));
    }
}
