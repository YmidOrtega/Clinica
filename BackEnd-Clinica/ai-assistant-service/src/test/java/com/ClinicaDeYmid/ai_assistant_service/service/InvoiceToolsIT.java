package com.ClinicaDeYmid.ai_assistant_service.service;

import com.ClinicaDeYmid.ai_assistant_service.support.JwtTestTokens;
import com.ClinicaDeYmid.ai_assistant_service.support.PostgresTestContainer;
import com.ClinicaDeYmid.ai_assistant_service.support.StubbedServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestContainer.class)
class InvoiceToolsIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(700);

    @Autowired
    private InvoiceTools tools;

    @Autowired
    private InvoiceProjection projection;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
        registry.add("spring.cloud.openfeign.client.config.billing-service.url", StubbedServices::baseUrl);
    }

    @BeforeEach
    void freshStubs() {
        StubbedServices.server().resetAll();
    }

    @Test
    void readsTheLocalStatusOfAnInvoiceWithItsFindings() {
        String number = issued(UUID.randomUUID(), "REJECTED", "900156264");

        assertThat(tools.invoiceStatus(number.toLowerCase()))
                .contains("\"dianStatus\":\"REJECTED\"", "DIAN_REJECTED", "\"cuv\":false");
        assertThat(tools.invoiceStatus("SETP000")).contains("no hay una factura emitida");
    }

    @Test
    void asksBillingForTheDetailAndSaysWhyWhenItCannot() {
        UUID invoice = UUID.randomUUID();
        String number = issued(invoice, "REJECTED", "900156264");
        StubbedServices.server().stubFor(get(urlPathEqualTo("/api/v1/billing/invoices/" + invoice + "/dian-verdicts"))
                .willReturn(okJson("[{\"statusCode\":\"99\",\"errors\":[\"FAD06: NIT del adquiriente no válido\"]}]")));
        StubbedServices.server().stubFor(get(urlPathEqualTo("/api/v1/billing/invoices/" + invoice + "/objections"))
                .willReturn(aResponse().withStatus(403)));
        StubbedServices.server().stubFor(get(urlPathEqualTo("/api/v1/billing/invoices/" + invoice + "/filing"))
                .willReturn(aResponse().withStatus(503)));

        assertThat(tools.dianVerdicts(number)).contains("FAD06");
        assertThat(tools.objections(number)).contains("no tiene permiso");
        assertThat(tools.filing(number)).contains("billing no respondió");
    }

    @Test
    void searchesTheIssuedInvoicesOfAPayerAndCutsLongAnswers() {
        String nit = "8" + (100000000 + SEQUENCE.incrementAndGet());
        String number = issued(UUID.randomUUID(), "ACCEPTED", nit);

        assertThat(tools.searchInvoices(nit, null, "2026-08-01", "2026-09-30")).contains(number);
        assertThat(tools.searchInvoices(nit, "REJECTED", "2026-08-01", "2026-09-30")).isEqualTo("[]");
        assertThat(InvoiceTools.limit("x".repeat(InvoiceTools.MAX_RESULT_LENGTH + 10))).endsWith("más específico]");
    }

    private String issued(UUID invoice, String dianStatus, String payerNit) {
        String number = "SETP99100" + SEQUENCE.incrementAndGet();
        Instant at = Instant.now();
        projection.follow(new InvoiceState(invoice, "InvoiceIssued", at, number, "SERVICES", "ISSUED",
                LocalDate.of(2026, 9, 1), "ADM-2026-000123", "PAYER", payerNit, "CT-1", null,
                new BigDecimal("45000.00"), BigDecimal.ZERO, new BigDecimal("45000.00"), null, dianStatus, at, null,
                null, null, "{\"creditNotes\":[],\"objections\":[]}"));
        return number;
    }
}
