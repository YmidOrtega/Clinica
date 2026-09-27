package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.application.DianDelivery;
import com.ClinicaDeYmid.billing_service.domain.Cufe;
import com.ClinicaDeYmid.billing_service.domain.DianEnvironment;
import com.ClinicaDeYmid.billing_service.support.BillingSetup;
import com.ClinicaDeYmid.billing_service.support.DianSimulator;
import com.ClinicaDeYmid.billing_service.support.JwtTestTokens;
import com.ClinicaDeYmid.billing_service.support.LocalDianSigningKey;
import com.ClinicaDeYmid.billing_service.support.XadesVerification;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Iterator;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CreditNoteApiIT extends InvoicingIntegrationTest {

    private static final String CREDIT_NOTES = "/api/v1/billing/credit-notes";

    @Autowired
    private DianDelivery delivery;

    @Test
    void voidsAnAcceptedInvoiceAndFreesItsUnitForANewInvoice() throws Exception {
        anActiveResolution("SETV");
        Episode episode = outpatient("COVERED");
        String sale = confirmedSale(episode);
        String invoice = acceptedInvoice(delivery, episode, sale);
        String cufe = JsonPath.read(as("BILLING", get(INVOICES + "/" + invoice)).andReturn().getResponse()
                .getContentAsString(), "$.cufe");

        String body = change("BILLING", post(INVOICES + "/" + invoice + "/credit-notes"), 1,
                "{\"concept\":\"VOID\",\"reason\":\"El pagador no correspondía\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(matchesPattern("^NC[0-9]+$")))
                .andExpect(jsonPath("$.conceptDianCode").value("2"))
                .andExpect(jsonPath("$.creditedGross").value(45000.00))
                .andExpect(jsonPath("$.creditedShare").value(35000.00))
                .andExpect(jsonPath("$.creditedPayable").value(10000.00))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.cude").value(matchesPattern("^[0-9a-f]{96}$")))
                .andExpect(jsonPath("$.signedAt").exists())
                .andReturn().getResponse().getContentAsString();
        String note = JsonPath.read(body, "$.uuid");
        String number = JsonPath.read(body, "$.number");

        as("BILLING", get(INVOICES + "/" + invoice))
                .andExpect(jsonPath("$.status.code").value("VOIDED"))
                .andExpect(jsonPath("$.status.reason").value(org.hamcrest.Matchers.containsString(number)))
                .andExpect(jsonPath("$.creditedTotal").value(10000.00));
        String ubl = as("BILLING", get(CREDIT_NOTES + "/" + note + "/ubl")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(XadesVerification.verify(ubl, LocalDianSigningKey.SHARED.certificate().getPublicKey()).valid())
                .isTrue();
        Document document = XadesVerification.parse(ubl);
        XPath path = xpath();
        assertThat(path.evaluate("/cn:CreditNote/cac:BillingReference/cac:InvoiceDocumentReference/cbc:UUID", document))
                .isEqualTo(cufe);
        assertThat(path.evaluate("/cn:CreditNote/cac:DiscrepancyResponse/cbc:ResponseCode", document)).isEqualTo("2");
        assertThat(path.evaluate("/cn:CreditNote/cbc:UUID/@schemeName", document)).isEqualTo("CUDE-SHA384");
        assertThat(path.evaluate("/cn:CreditNote/cbc:UUID", document)).isEqualTo(Cufe.of(new Cufe.Input(
                path.evaluate("/cn:CreditNote/cbc:ID", document),
                LocalDate.parse(path.evaluate("/cn:CreditNote/cbc:IssueDate", document)),
                LocalTime.parse(path.evaluate("/cn:CreditNote/cbc:IssueTime", document).substring(0, 8)),
                new BigDecimal(path.evaluate("//cac:LegalMonetaryTotal/cbc:LineExtensionAmount", document)),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal(path.evaluate("//cac:LegalMonetaryTotal/cbc:PayableAmount", document)),
                path.evaluate("//cac:AccountingSupplierParty//cac:PartyTaxScheme/cbc:CompanyID", document),
                path.evaluate("//cac:AccountingCustomerParty//cac:PartyTaxScheme/cbc:CompanyID", document),
                JwtTestTokens.DIAN_SOFTWARE_PIN, DianEnvironment.TEST)));

        change("BILLING", post(INVOICES + "/" + invoice + "/credit-notes"), 2,
                "{\"concept\":\"VOID\",\"reason\":\"Otra vez\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVOICE_NOT_CREDITABLE"));
        as("BILLING", post(INVOICES), drafting(episode, sale)).andExpect(status().isCreated());

        DianSimulator.doesNotKnowTheDocument();
        DianSimulator.receivesTheTestSet("zip-note");
        as("BILLING", post(CREDIT_NOTES + "/" + note + "/dian-delivery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dian.status").value("AWAITING_VALIDATION"))
                .andExpect(jsonPath("$.dian.trackId").value("zip-note"));
        as("BILLING", get(CREDIT_NOTES + "/" + note + "/dian-verdicts"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].operation").value("SEND_TEST_SET"));
        as("BILLING", get(INVOICES + "/" + invoice + "/credit-notes"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].number").value(number));
    }

    @Test
    void creditsPartOfAnInvoiceWithoutExceedingWhatThePayerOwes() throws Exception {
        anActiveResolution("SETC");
        String invoice = acceptedInvoice(delivery);

        change("BILLING", post(INVOICES + "/" + invoice + "/credit-notes"), 1, partial("PRICE_ADJUSTMENT",
                "\"amount\":4000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creditedPayable").value(4000.00))
                .andExpect(jsonPath("$.creditedShare").value(0))
                .andExpect(jsonPath("$.lines[0].invoiceLinePosition").value(1));
        as("BILLING", get(INVOICES + "/" + invoice))
                .andExpect(jsonPath("$.status.code").value("ISSUED"))
                .andExpect(jsonPath("$.creditedTotal").value(4000.00));

        change("BILLING", post(INVOICES + "/" + invoice + "/credit-notes"), 1, partial("DISCOUNT", "\"amount\":1000"))
                .andExpect(status().isPreconditionFailed());
        change("BILLING", post(INVOICES + "/" + invoice + "/credit-notes"), 2, partial("DISCOUNT", "\"amount\":7000"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CREDIT_EXCEEDS_INVOICE"));
        change("BILLING", post(INVOICES + "/" + invoice + "/credit-notes"), 2, partial("PARTIAL_RETURN",
                "\"quantity\":2"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CREDIT_EXCEEDS_INVOICE"));
        change("BILLING", post(INVOICES + "/" + invoice + "/credit-notes"), 2,
                "{\"concept\":\"DISCOUNT\",\"reason\":\"x\",\"lines\":[{\"invoiceLinePosition\":9,\"amount\":10}]}")
                .andExpect(status().isBadRequest());
        change("BILLING", post(INVOICES + "/" + invoice + "/credit-notes"), 2,
                "{\"concept\":\"VOID\",\"reason\":\"Anular el resto\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVOICE_NOT_CREDITABLE"));
        change("BILLING", post(INVOICES + "/" + invoice + "/credit-notes"), 2, partial("DISCOUNT", "\"amount\":6000"))
                .andExpect(status().isCreated());
        as("BILLING", get(INVOICES + "/" + invoice)).andExpect(jsonPath("$.creditedTotal").value(10000.00));
    }

    @Test
    void onlyAnInvoiceAcceptedByTheDianIsCreditedAndOnlyWithARecentSecondFactor() throws Exception {
        anActiveResolution("SETN");
        String invoice = issued();

        changeWithToken(JwtTestTokens.bearerWithoutSecondFactor("BILLING"),
                post(INVOICES + "/" + invoice + "/credit-notes"), 1, "{\"concept\":\"VOID\",\"reason\":\"x\"}")
                .andExpect(status().isUnauthorized());
        as("RECEPTIONIST", post(INVOICES + "/" + invoice + "/credit-notes"),
                "{\"concept\":\"VOID\",\"reason\":\"x\"}").andExpect(status().isForbidden());
        change("BILLING", post(INVOICES + "/" + invoice + "/credit-notes"), 1,
                "{\"concept\":\"VOID\",\"reason\":\"x\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVOICE_NOT_CREDITABLE"));
    }

    @Test
    void eachPrefixKeepsItsOwnConsecutive() throws Exception {
        anActiveResolution("SETX");
        String invoice = acceptedInvoice(delivery);
        change("ADMIN", put(BillingSetup.ISSUER + "/credit-note-prefix"), 0, "{\"prefix\":\"nc-1\"}")
                .andExpect(status().isBadRequest());
        change("ADMIN", put(BillingSetup.ISSUER + "/credit-note-prefix"), 0, "{\"prefix\":\"NCX\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditNotePrefix").value("NCX"));

        change("BILLING", post(INVOICES + "/" + invoice + "/credit-notes"), 1, partial("DISCOUNT", "\"amount\":100"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("NCX1"));
        change("BILLING", post(INVOICES + "/" + invoice + "/credit-notes"), 2, partial("DISCOUNT", "\"amount\":100"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("NCX2"));
    }

    private static String partial(String concept, String credit) {
        return "{\"concept\":\"" + concept + "\",\"reason\":\"Glosa conciliada\",\"lines\":[{\"invoiceLinePosition\":1,"
                + credit + "}]}";
    }

    private static XPath xpath() {
        XPath path = XPathFactory.newInstance().newXPath();
        Map<String, String> namespaces = Map.of(
                "cn", "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2",
                "cac", "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2",
                "cbc", "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2");
        path.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return namespaces.getOrDefault(prefix, XMLConstants.NULL_NS_URI);
            }

            @Override
            public String getPrefix(String uri) {
                return null;
            }

            @Override
            public Iterator<String> getPrefixes(String uri) {
                return null;
            }
        });
        return path;
    }
}
