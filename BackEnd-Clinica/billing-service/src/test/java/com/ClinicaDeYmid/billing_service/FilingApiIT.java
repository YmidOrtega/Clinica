package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.application.DianDelivery;
import com.ClinicaDeYmid.billing_service.application.clinical.ClinicalProjection;
import com.ClinicaDeYmid.billing_service.support.MinistrySimulator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FilingApiIT extends InvoicingIntegrationTest {

    private static final String PENDING = "/api/v1/billing/filings/pending";

    @Autowired
    private DianDelivery delivery;

    @Autowired
    private ClinicalProjection clinical;

    @Test
    void registersTheFilingWithTheCuvAndCorrectsItWithAReason() throws Exception {
        String invoice = validated("RADA");

        as("BILLING", get(filingOf(invoice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filed").value(false))
                .andExpect(jsonPath("$.cuv").value(MinistrySimulator.CUV))
                .andExpect(jsonPath("$.state").value("ON_TIME"))
                .andExpect(jsonPath("$.remainingBusinessDays").value(22));
        as("BILLING", post(filingOf(invoice)), registration("RAD-2026-001", TODAY))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.filed").value(true))
                .andExpect(jsonPath("$.filingNumber").value("RAD-2026-001"))
                .andExpect(jsonPath("$.cuv").value(MinistrySimulator.CUV))
                .andExpect(jsonPath("$.late").value(false))
                .andExpect(jsonPath("$.state").doesNotExist());
        as("BILLING", post(filingOf(invoice)), registration("RAD-2026-009", TODAY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_FILED"));
        as("BILLING", get(PENDING)).andExpect(jsonPath("$[?(@.invoiceUuid == '" + invoice + "')]").isEmpty());

        as("BILLING", put(filingOf(invoice)), correction("RAD-2026-002", "Se digitó mal el radicado"))
                .andExpect(status().isPreconditionRequired());
        change("BILLING", put(filingOf(invoice)), 0, correction("RAD-2026-002", "Se digitó mal el radicado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filingNumber").value("RAD-2026-002"))
                .andExpect(jsonPath("$.correctionReason").value("Se digitó mal el radicado"));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM billing_history.invoice_filings_aud a
                JOIN invoice_filings f ON f.id = a.id JOIN invoices i ON i.id = f.invoice_id WHERE i.uuid = ?""",
                Integer.class, invoice)).isEqualTo(2);
    }

    @Test
    void onlyAnInvoiceWithCuvIsFiledAndNeverWithAnImpossibleDate() throws Exception {
        anActiveResolution("RADB");
        Episode episode = outpatient("COVERED");
        String withoutCuv = issued(episode, confirmedSale(episode));
        String copayment = copaymentCollected(outpatient("COVERED"), "35000");

        as("BILLING", post(filingOf(withoutCuv)), registration("RAD-1", TODAY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FILING_WITHOUT_CUV"));
        as("BILLING", get(INVOICES + "/" + withoutCuv + "/filing-package"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FILING_PACKAGE_NOT_READY"));
        as("BILLING", post(filingOf(copayment)), registration("RAD-1", TODAY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NOT_FILEABLE"));
        as("RECEPTIONIST", post(filingOf(withoutCuv)), registration("RAD-1", TODAY))
                .andExpect(status().isForbidden());

        String invoice = validated("RADC");
        as("BILLING", post(filingOf(invoice)), registration("RAD-1", TODAY.plusDays(1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_FILING_DATE"));
        as("BILLING", post(filingOf(invoice)), registration("RAD-1", TODAY.minusDays(1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_FILING_DATE"));
        as("BILLING", post(filingOf(invoice)), "{\"filingNumber\":\" \",\"filedOn\":\"" + TODAY + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void theTrayShowsWhatIsOverdueAndALateFilingSaysSo() throws Exception {
        String invoice = validated("RADD");
        jdbc.update("UPDATE invoices SET issued_on = ? WHERE uuid = ?", TODAY.minusDays(60), invoice);

        as("BILLING", get(PENDING).param("state", "OVERDUE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.invoiceUuid == '" + invoice + "')].state").value("OVERDUE"))
                .andExpect(jsonPath("$[?(@.invoiceUuid == '" + invoice + "')].remainingBusinessDays")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.lessThan(0))))
                .andExpect(jsonPath("$[?(@.invoiceUuid == '" + invoice + "')].payerName").value("Nueva EPS S.A."));
        as("BILLING", get(PENDING).param("state", "ON_TIME"))
                .andExpect(jsonPath("$[?(@.invoiceUuid == '" + invoice + "')]").isEmpty());
        as("BILLING", get(PENDING).param("payerUuid", UUID.randomUUID().toString()))
                .andExpect(jsonPath("$[?(@.invoiceUuid == '" + invoice + "')]").isEmpty());

        as("BILLING", post(filingOf(invoice)), registration("RAD-TARDE", TODAY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.late").value(true));
    }

    @Test
    void packsWhatThePayerReceives() throws Exception {
        String invoice = validated("RADE");
        String number = numberOf(invoice);

        byte[] zip = as("BILLING", get(INVOICES + "/" + invoice + "/filing-package"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andReturn().getResponse().getContentAsByteArray();

        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream read = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = read.getNextEntry(); entry != null; entry = read.getNextEntry()) {
                entries.put(entry.getName(), new String(read.readAllBytes(), StandardCharsets.ISO_8859_1));
            }
        }
        assertThat(entries).containsOnlyKeys(number + ".xml", number + ".pdf", number + "_RIPS.json",
                number + "_CUV.json");
        assertThat(entries.get(number + ".xml")).contains("<AttachedDocument");
        assertThat(entries.get(number + ".pdf")).startsWith("%PDF");
        assertThat(entries.get(number + "_RIPS.json")).contains("\"numFactura\":\"" + number + "\"");
        assertThat(entries.get(number + "_CUV.json")).contains(MinistrySimulator.CUV);
    }

    private String validated(String prefix) throws Exception {
        anActiveResolution(prefix);
        Episode episode = outpatient("COVERED");
        String sale = confirmedSale(episode);
        documentedCare(clinical, episode);
        String invoice = acceptedInvoice(delivery, episode, sale);
        MinistrySimulator.logsIn();
        MinistrySimulator.validates(numberOf(invoice));
        as("BILLING", post(INVOICES + "/" + invoice + "/rips-validation"))
                .andExpect(jsonPath("$.status").value("VALIDATED"));
        return invoice;
    }

    private static String filingOf(String invoice) {
        return INVOICES + "/" + invoice + "/filing";
    }

    private static String registration(String number, java.time.LocalDate filedOn) {
        return "{\"filingNumber\":\"" + number + "\",\"filedOn\":\"" + filedOn + "\"}";
    }

    private static String correction(String number, String reason) {
        return "{\"filingNumber\":\"" + number + "\",\"filedOn\":\"" + TODAY + "\",\"reason\":\"" + reason + "\"}";
    }
}
