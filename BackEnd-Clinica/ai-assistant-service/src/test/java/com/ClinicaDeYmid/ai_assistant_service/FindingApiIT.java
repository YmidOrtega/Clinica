package com.ClinicaDeYmid.ai_assistant_service;

import com.ClinicaDeYmid.ai_assistant_service.repository.FindingRepository;
import com.ClinicaDeYmid.ai_assistant_service.repository.entity.Finding;
import com.ClinicaDeYmid.ai_assistant_service.shared.FindingRule;
import com.ClinicaDeYmid.ai_assistant_service.support.JwtTestTokens;
import com.ClinicaDeYmid.ai_assistant_service.support.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class FindingApiIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(500);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FindingRepository findings;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
    }

    @Test
    void theTrayShowsTheSeriousAndTheUrgentFirstAndFiltersByInvoice() throws Exception {
        String number = nextNumber();
        UUID invoice = UUID.randomUUID();
        Instant now = Instant.now();
        findings.save(Finding.open(invoice, number, FindingRule.BILLED_WITHOUT_CONTRACT, null, "Sin contrato", null, now));
        findings.save(Finding.open(invoice, number, FindingRule.FILING_DUE_SOON, null, "Radicar pronto",
                LocalDate.of(2026, 10, 2), now));
        findings.save(Finding.open(invoice, number, FindingRule.DIAN_REJECTED, null, "Rechazada", null, now));

        as("BILLING", "/api/v1/assistant/findings?invoiceNumber=" + number)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].rule").value("DIAN_REJECTED"))
                .andExpect(jsonPath("$.content[1].rule").value("FILING_DUE_SOON"))
                .andExpect(jsonPath("$.content[1].dueOn").value("2026-10-02"))
                .andExpect(jsonPath("$.content[2].severity").value("LOW"));
        as("ACCOUNTS_RECEIVABLE", "/api/v1/assistant/findings?severity=HIGH&invoiceNumber=" + number)
                .andExpect(jsonPath("$.totalElements").value(1));
        as("BILLING", "/api/v1/assistant/invoices/" + number + "/findings").andExpect(status().isOk());
        as("BILLING", "/api/v1/assistant/findings/summary")
                .andExpect(jsonPath("$.high").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.byRule").isArray());
    }

    @Test
    void resolvedFindingsLeaveTheDefaultTrayButStayInTheHistory() throws Exception {
        String number = nextNumber();
        Finding finding = Finding.open(UUID.randomUUID(), number, FindingRule.COPAY_SHORTFALL, null, "Copago", null,
                Instant.now());
        finding.resolve(Instant.now());
        findings.save(finding);

        as("BILLING", "/api/v1/assistant/findings?invoiceNumber=" + number).andExpect(jsonPath("$.totalElements").value(0));
        as("BILLING", "/api/v1/assistant/findings?status=RESOLVED&invoiceNumber=" + number)
                .andExpect(jsonPath("$.content[0].resolvedAt").exists());
    }

    @Test
    void onlyInvoicingAndReceivablesSeeTheTray() throws Exception {
        as("RECEPTIONIST", "/api/v1/assistant/findings").andExpect(status().isForbidden());
        as("BILLING", "/api/v1/assistant/findings?invoiceNumber=../x").andExpect(status().isBadRequest());
    }

    private ResultActions as(String role, String path) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role)));
    }

    private static String nextNumber() {
        return "SETP99000" + SEQUENCE.incrementAndGet();
    }
}
