package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.application.InvoiceNumbering;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.IssuedNumber;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.LongStream;

import static com.ClinicaDeYmid.billing_service.support.BillingSetup.ISSUER;
import static com.ClinicaDeYmid.billing_service.support.BillingSetup.RESOLUTIONS;
import static com.ClinicaDeYmid.billing_service.support.BillingSetup.configuration;
import static com.ClinicaDeYmid.billing_service.support.BillingSetup.resolution;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvoiceNumberingIT extends IntegrationTest {

    @Autowired
    private InvoiceNumbering numbering;

    @Autowired
    private TransactionTemplate transactions;

    @BeforeEach
    void startWithAnIssuer() throws Exception {
        forgetTheBillingSetup();
        as("ADMIN", post(ISSUER), configuration()).andExpect(status().isCreated());
    }

    @Test
    void issuesConsecutiveNumbersFromTheActiveResolution() throws Exception {
        activeResolution(990000000, 995000000);

        assertThat(List.of(take(), take(), take())).extracting(IssuedNumber::formatted)
                .containsExactly("SETP990000000", "SETP990000001", "SETP990000002");
    }

    @Test
    void aNumberTakenByAnInvoiceThatRollsBackIsNotLost() throws Exception {
        activeResolution(1, 100);

        transactions.executeWithoutResult(status -> {
            assertThat(numbering.next().consecutive()).isEqualTo(1);
            status.setRollbackOnly();
        });

        assertThat(take().consecutive()).isEqualTo(1);
    }

    @Test
    void neverHandsOutTheSameNumberTwiceUnderConcurrency() throws Exception {
        activeResolution(1, 1000);
        int invoices = 40;
        List<Callable<Long>> tasks = new ArrayList<>();
        for (int i = 0; i < invoices; i++) {
            tasks.add(() -> take().consecutive());
        }

        List<Long> taken = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (Future<Long> result : pool.invokeAll(tasks)) {
                taken.add(result.get());
            }
        }

        assertThat(taken).containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, invoices).boxed().toList());
    }

    @Test
    void theLastNumberExhaustsTheResolutionAndStopsInvoicing() throws Exception {
        String resolution = activeResolution(1, 2);

        take();
        assertThat(take().consecutive()).isEqualTo(2);

        as("BILLING", get(RESOLUTIONS + "/" + resolution))
                .andExpect(jsonPath("$.status.code").value("EXHAUSTED"))
                .andExpect(jsonPath("$.remaining").value(0));
        assertThatThrownBy(this::take).isInstanceOf(BillingException.NoActiveResolution.class);
    }

    @Test
    void refusesToNumberWithoutAnActiveResolution() {
        assertThatThrownBy(this::take).isInstanceOf(BillingException.NoActiveResolution.class);
    }

    @Test
    void onlyNumbersInsideTheInvoiceTransaction() {
        assertThatThrownBy(() -> numbering.next()).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void takingNumbersLeavesNoTraceInTheResolutionHistory() throws Exception {
        activeResolution(1, 100);
        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM billing_history.numbering_resolutions_aud", Integer.class);

        take();
        take();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billing_history.numbering_resolutions_aud", Integer.class))
                .isEqualTo(before);
    }

    private IssuedNumber take() {
        return transactions.execute(status -> numbering.next());
    }

    private String activeResolution(long from, long to) throws Exception {
        String body = as("ADMIN", post(RESOLUTIONS), resolution("18760000001", "SETP", from, to))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String uuid = JsonPath.read(body, "$.uuid");
        change("ADMIN", post(RESOLUTIONS + "/" + uuid + "/activation"), 0, null).andExpect(status().isOk());
        return uuid;
    }
}
