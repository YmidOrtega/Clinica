package com.ClinicaDeYmid.ai_assistant_service;

import com.ClinicaDeYmid.ai_assistant_service.service.ActionService;
import com.ClinicaDeYmid.ai_assistant_service.service.ConversationService;
import com.ClinicaDeYmid.ai_assistant_service.service.InvoiceProjection;
import com.ClinicaDeYmid.ai_assistant_service.service.InvoiceState;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class FilingAndObjectionActionIT {

    private static final UUID BILLER = UUID.fromString(JwtTestTokens.USERS.get("BILLING"));
    private static final AtomicInteger SEQUENCE = new AtomicInteger(900);

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
    void theFilingTheUserConfirmsIsRegisteredWithTheNumberAndDateTheyGave() throws Exception {
        UUID invoice = UUID.randomUUID();
        String number = issued(invoice, "[]");
        String action = actions.proposeFiling(BILLER, conversations.start(BILLER, "Radicar").uuid(), number,
                "RAD-2026-0091", LocalDate.of(2026, 9, 10), "La factura tiene CUV y aún no se radica").uuid().toString();
        StubbedServices.server().stubFor(post(urlPathEqualTo("/api/v1/billing/invoices/" + invoice + "/filing"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"filingNumber\":\"RAD-2026-0091\",\"late\":false}")));

        as(JwtTestTokens.bearer("ACCOUNTS_RECEIVABLE"), confirmation(action, 0)).andExpect(status().isNotFound());
        as(JwtTestTokens.bearer("BILLING"), confirmation(action, 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.outcome").value("Radicada con el número RAD-2026-0091"));

        StubbedServices.server().verify(postRequestedFor(urlPathEqualTo("/api/v1/billing/invoices/" + invoice + "/filing"))
                .withRequestBody(equalToJson("{\"filingNumber\":\"RAD-2026-0091\",\"filedOn\":\"2026-09-10\"}")));
        assertThatThrownBy(() -> actions.proposeFiling(BILLER, conversations.start(BILLER, "x").uuid(), number,
                "RAD-1", LocalDate.now().plusDays(3), "x")).hasMessageContaining("futura");
    }

    @Test
    void theModelPreparesTheGlossAnswerAndItRunsOnlyWithARecentSecondFactor() throws Exception {
        UUID invoice = UUID.randomUUID();
        UUID gloss = UUID.randomUUID();
        String number = issued(invoice, glossAwaiting(gloss));
        StubbedServices.server().stubFor(get(urlPathEqualTo("/api/v1/billing/objections/" + gloss))
                .willReturn(okJson("{\"uuid\":\"" + gloss + "\"}").withHeader(HttpHeaders.ETAG, "\"3\"")));
        String conversation = conversations.start(BILLER, "Glosa GL-778").uuid().toString();
        ModelSimulator.callsATool("proposeObjectionAnswer", """
                {"invoiceNumber":"%s","payerRecord":"GL-778","responseRecord":"RP-55","respondedOn":"%s",
                 "answers":[{"position":1,"code":"RE9801","acceptedAmount":3000,"detail":"Se acepta la diferencia"}],
                 "reason":"El usuario acepta 3000 de la glosa"}""".formatted(number, LocalDate.now()),
                "Dejé preparada la respuesta; confírmala con tu segundo factor.");

        String action = JsonPath.read(as(JwtTestTokens.bearer("BILLING"),
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/assistant/conversations/" + conversation + "/messages")
                                .content("{\"content\":\"Responde la GL-778 aceptando 3000 con RE9801, respuesta RP-55\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.proposedActions[0].kind").value("ANSWER_OBJECTION"))
                .andExpect(jsonPath("$.proposedActions[0].details.answers[0].code").value("RE9801"))
                .andReturn().getResponse().getContentAsString(), "$.proposedActions[0].uuid");
        StubbedServices.server().stubFor(post(urlPathEqualTo("/api/v1/billing/objections/" + gloss + "/response"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"acceptedAmount\":3000.00,\"creditNoteNumber\":\"NC1\"}")));

        as(JwtTestTokens.bearerWithoutSecondFactor("BILLING"), confirmation(action, 0))
                .andExpect(status().isUnauthorized());
        StubbedServices.server().verify(0, postRequestedFor(urlPathEqualTo("/api/v1/billing/objections/" + gloss + "/response")));
        as(JwtTestTokens.bearer("BILLING"), confirmation(action, 0))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.outcome").value("Respondida; valor aceptado 3000.00 con la nota crédito NC1"));
        StubbedServices.server().verify(postRequestedFor(urlPathEqualTo("/api/v1/billing/objections/" + gloss + "/response"))
                .withHeader(HttpHeaders.IF_MATCH, equalTo("\"3\""))
                .withRequestBody(equalToJson("""
                        {"responseRecord":"RP-55","respondedOn":"%s","answers":[{"position":1,"code":"RE9801",
                         "acceptedAmount":3000,"detail":"Se acepta la diferencia"}]}""".formatted(LocalDate.now()))));
    }

    @Test
    void aGlossThatChangedMeanwhileIsNotAnsweredAndBadDraftsAreRefused() throws Exception {
        UUID invoice = UUID.randomUUID();
        UUID gloss = UUID.randomUUID();
        String number = issued(invoice, glossAwaiting(gloss));
        StubbedServices.server().stubFor(get(urlPathEqualTo("/api/v1/billing/objections/" + gloss))
                .willReturn(okJson("{}").withHeader(HttpHeaders.ETAG, "\"1\"")));
        UUID conversation = conversations.start(BILLER, "Glosa").uuid();
        List<ActionService.ObjectionAnswer> answers = List.of(new ActionService.ObjectionAnswer(1, "re9602", null, "Soporte"));
        String action = actions.proposeObjectionAnswer(BILLER, conversation, number, "gl-778", "RP-56", LocalDate.now(),
                answers, "Se sustenta").uuid().toString();
        StubbedServices.server().stubFor(post(urlPathEqualTo("/api/v1/billing/objections/" + gloss + "/response"))
                .willReturn(aResponse().withStatus(412)));

        as(JwtTestTokens.bearer("BILLING"), confirmation(action, 0))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.outcome").value("la glosa cambió desde que se preparó la respuesta; pida una propuesta nueva"));
        assertThatThrownBy(() -> actions.proposeObjectionAnswer(BILLER, conversation, number, "GL-778", "RP-57",
                LocalDate.now(), List.of(new ActionService.ObjectionAnswer(1, "TA0201", null, null)), "x"))
                .hasMessageContaining("código RE");
        assertThatThrownBy(() -> actions.proposeObjectionAnswer(BILLER, conversation, number, "GL-000", "RP-57",
                LocalDate.now(), answers, "x")).hasMessageContaining("esperando respuesta");
    }

    private static String glossAwaiting(UUID gloss) {
        return "[{\"uuid\":\"" + gloss + "\",\"kind\":\"GLOSS\",\"payerRecord\":\"GL-778\",\"status\":\"AWAITING_RESPONSE\"}]";
    }

    private static MockHttpServletRequestBuilder confirmation(String action, long version) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/assistant/actions/" + action + "/confirmation")
                .header(HttpHeaders.IF_MATCH, "\"" + version + "\"");
    }

    private String issued(UUID invoice, String objections) {
        String number = "SETP99300" + SEQUENCE.incrementAndGet();
        projection.follow(new InvoiceState(invoice, "InvoiceObjectionRegistered", Instant.now(), number, "SERVICES",
                "ISSUED", LocalDate.of(2026, 9, 1), "ADM-2026-000910", "PAYER", "900156264", "CT-1", null,
                new BigDecimal("45000.00"), BigDecimal.ZERO, new BigDecimal("45000.00"), null, "ACCEPTED",
                Instant.now(), "c".repeat(96), null, null, "{\"creditNotes\":[],\"objections\":" + objections + "}"));
        return number;
    }

    private ResultActions as(String bearer, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer));
    }
}
