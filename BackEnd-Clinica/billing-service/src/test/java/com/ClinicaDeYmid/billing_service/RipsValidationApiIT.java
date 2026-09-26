package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.application.DianDelivery;
import com.ClinicaDeYmid.billing_service.application.clinical.ClinicalFact;
import com.ClinicaDeYmid.billing_service.application.clinical.ClinicalProjection;
import com.ClinicaDeYmid.billing_service.application.rips.MinistryValidation;
import com.ClinicaDeYmid.billing_service.support.DianSimulator;
import com.ClinicaDeYmid.billing_service.support.MinistrySimulator;
import com.ClinicaDeYmid.billing_service.support.StubbedServices;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RipsValidationApiIT extends InvoicingIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final String SUBMIT = MinistrySimulator.PATH + "/api/PaquetesFevRips/CargarFevRips";

    @Autowired
    private ClinicalProjection clinical;

    @Autowired
    private DianDelivery delivery;

    @Autowired
    private MinistryValidation validation;

    @Test
    void sendsTheRipsAndTheAttachedDocumentAndKeepsTheCuv() throws Exception {
        Accepted invoice = acceptedWithCompleteRips("MUVA");
        MinistrySimulator.logsIn();
        MinistrySimulator.validates(invoice.number());

        String body = as("BILLING", post(validationOf(invoice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.sequence").value(1))
                .andExpect(jsonPath("$.cuv").value(MinistrySimulator.CUV))
                .andExpect(jsonPath("$.processId").value(1024))
                .andExpect(jsonPath("$.recovered").value(false))
                .andExpect(jsonPath("$.findings[0].code").value("FED078"))
                .andReturn().getResponse().getContentAsString();

        List<LoggedRequest> sent = StubbedServices.server().findAll(postRequestedFor(urlPathEqualTo(SUBMIT)));
        String request = sent.getLast().getBodyAsString();
        assertThat((String) JsonPath.read(request, "$.rips.numFactura")).isEqualTo(invoice.number());
        String container = new String(Base64.getDecoder().decode((String) JsonPath.read(request, "$.xmlFevFile")),
                StandardCharsets.UTF_8);
        assertThat(container).contains("<AttachedDocument", "<cbc:ParentDocumentID>" + invoice.number());
        assertThat(as("BILLING", get(INVOICES + "/" + invoice.uuid() + "/rips-validations/"
                + JsonPath.read(body, "$.uuid") + "/response")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).contains(MinistrySimulator.CUV);
        as("BILLING", post(validationOf(invoice)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RIPS_ALREADY_VALIDATED"));
    }

    @Test
    void aRejectionKeepsTheFindingsAndACorrectedSubmissionIsANewOne() throws Exception {
        Accepted invoice = acceptedWithCompleteRips("MUVB");
        MinistrySimulator.logsIn();
        MinistrySimulator.rejects(invoice.number(), "RVC019", "El código de diagnóstico no es válido");

        as("BILLING", post(validationOf(invoice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.cuv").doesNotExist())
                .andExpect(jsonPath("$.findings[0].kind").value("RECHAZADO"))
                .andExpect(jsonPath("$.findings[0].code").value("RVC019"))
                .andExpect(jsonPath("$.findings[0].path").value("usuarios[0]"));
        MinistrySimulator.validates(invoice.number());
        as("BILLING", post(validationOf(invoice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.sequence").value(2));

        as("BILLING", get(INVOICES + "/" + invoice.uuid() + "/rips-validations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("VALIDATED"))
                .andExpect(jsonPath("$[1].status").value("REJECTED"));
    }

    @Test
    void anUnreachableValidatorLeavesTheSubmissionPendingUntilARetryGetsTheCuv() throws Exception {
        Accepted invoice = acceptedWithCompleteRips("MUVC");
        MinistrySimulator.logsIn();
        MinistrySimulator.isDown();

        as("BILLING", post(validationOf(invoice)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MINISTRY_VALIDATOR_UNAVAILABLE"));
        as("BILLING", get(INVOICES + "/" + invoice.uuid() + "/rips-validations"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].attempts").value(1))
                .andExpect(jsonPath("$[0].lastFailure").exists());

        MinistrySimulator.validates(invoice.number());
        validation.retryPending(50);

        as("BILLING", get(INVOICES + "/" + invoice.uuid() + "/rips-validations"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("VALIDATED"))
                .andExpect(jsonPath("$[0].attempts").value(2));
    }

    @Test
    void recoversTheCuvOfAnInvoiceTheMinistryAlreadyValidated() throws Exception {
        Accepted invoice = acceptedWithCompleteRips("MUVD");
        MinistrySimulator.logsIn();
        MinistrySimulator.alreadyValidated(invoice.number());

        as("BILLING", post(validationOf(invoice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.recovered").value(true))
                .andExpect(jsonPath("$.cuv").value(MinistrySimulator.CUV));
    }

    @Test
    void onlyACompleteRipsOfAnInvoiceAcceptedByTheDianIsSent() throws Exception {
        anActiveResolution("MUVE");
        Episode episode = outpatient("COVERED");
        String incomplete = issued(episode, confirmedSale(episode));
        as("BILLING", post(INVOICES + "/" + incomplete + "/rips-validation"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RIPS_INCOMPLETE"));

        Episode other = outpatient("COVERED");
        String notAccepted = issued(other, confirmedSale(other));
        documentedCare(other);
        as("BILLING", post(INVOICES + "/" + notAccepted + "/rips-validation"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATTACHED_DOCUMENT_NOT_READY"));
        as("RECEPTIONIST", post(INVOICES + "/" + notAccepted + "/rips-validation"))
                .andExpect(status().isForbidden());
        as("BILLING", get(INVOICES + "/" + notAccepted + "/rips-validations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private static String validationOf(Accepted invoice) {
        return INVOICES + "/" + invoice.uuid() + "/rips-validation";
    }

    private Accepted acceptedWithCompleteRips(String prefix) throws Exception {
        anActiveResolution(prefix);
        Episode episode = outpatient("COVERED");
        String invoice = issued(episode, confirmedSale(episode));
        documentedCare(episode);
        DianSimulator.receivesTheTestSet("zip-" + prefix);
        DianSimulator.doesNotKnowTheDocument();
        delivery.deliverPending(50);
        DianSimulator.validates("GetStatusZip");
        delivery.checkPending(50);
        String number = JsonPath.read(as("BILLING", get(INVOICES + "/" + invoice))
                .andExpect(jsonPath("$.dian.status").value("ACCEPTED"))
                .andReturn().getResponse().getContentAsString(), "$.number");
        return new Accepted(invoice, number);
    }

    private void documentedCare(Episode episode) {
        UUID encounter = UUID.randomUUID();
        Instant opened = LocalDate.now(BOGOTA).atTime(9, 30).atZone(BOGOTA).toInstant();
        clinical.follow(new ClinicalFact.EncounterOpened(encounter, episode.uuid(), UUID.randomUUID(), "OUTPATIENT",
                opened, new ClinicalFact.CareSetting("328", "01", "01")));
        clinical.follow(new ClinicalFact.NoteSigned(UUID.randomUUID(), encounter, episode.uuid(), "CONSULTATION",
                opened.plusSeconds(600), "15", "38",
                List.of(new ClinicalFact.CodedDiagnosis("I10X", "PRINCIPAL", "CONFIRMED_NEW"))));
    }

    private record Accepted(String uuid, String number) {
    }
}
