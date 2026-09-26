package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.application.clinical.ClinicalFact;
import com.ClinicaDeYmid.billing_service.application.clinical.ClinicalProjection;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RipsApiIT extends InvoicingIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Autowired
    private ClinicalProjection clinical;

    @Test
    void buildsTheRipsOfAnIssuedInvoiceFromBillingAndClinicalFacts() throws Exception {
        anActiveResolution("RIPA");
        Episode episode = outpatient("COVERED");
        String sale = confirmedSale(episode);
        String invoice = issued(episode, sale);
        UUID encounter = UUID.randomUUID();
        Instant opened = LocalDate.now(BOGOTA).atTime(9, 30).atZone(BOGOTA).toInstant();
        clinical.follow(new ClinicalFact.EncounterOpened(encounter, episode.uuid(), UUID.randomUUID(), "OUTPATIENT", opened,
                new ClinicalFact.CareSetting("328", "01", "01")));
        clinical.follow(new ClinicalFact.NoteSigned(UUID.randomUUID(), encounter, episode.uuid(), "CONSULTATION",
                opened.plusSeconds(600), "15", "38",
                List.of(new ClinicalFact.CodedDiagnosis("I10X", "PRINCIPAL", "CONFIRMED_NEW"))));
        UUID voided = UUID.randomUUID();
        clinical.follow(new ClinicalFact.NoteSigned(voided, encounter, episode.uuid(), "CONSULTATION",
                opened.plusSeconds(900), "44", "26",
                List.of(new ClinicalFact.CodedDiagnosis("Z000", "PRINCIPAL", "IMPRESSION"))));
        clinical.follow(new ClinicalFact.NoteVoided(voided));

        as("BILLING", get(INVOICES + "/" + invoice + "/rips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.gaps.length()").value(0))
                .andExpect(jsonPath("$.rips.numDocumentoIdObligado").value("800197268"))
                .andExpect(jsonPath("$.rips.usuarios[0].tipoUsuario").value("02"))
                .andExpect(jsonPath("$.rips.usuarios[0].codMunicipioResidencia").value("68001"))
                .andExpect(jsonPath("$.rips.usuarios[0].serviciosTecnologias.consultas[0].codConsulta").value("890201"))
                .andExpect(jsonPath("$.rips.usuarios[0].serviciosTecnologias.consultas[0].fechaInicioAtencion")
                        .value(LocalDate.now(BOGOTA) + " 09:30"))
                .andExpect(jsonPath("$.rips.usuarios[0].serviciosTecnologias.consultas[0].codServicio").value(328))
                .andExpect(jsonPath("$.rips.usuarios[0].serviciosTecnologias.consultas[0].finalidadTecnologiaSalud").value("15"))
                .andExpect(jsonPath("$.rips.usuarios[0].serviciosTecnologias.consultas[0].codDiagnosticoPrincipal").value("I10X"))
                .andExpect(jsonPath("$.rips.usuarios[0].serviciosTecnologias.consultas[0].numDocumentoIdentificacion")
                        .value("80100200"))
                .andExpect(jsonPath("$.rips.usuarios[0].serviciosTecnologias.consultas[0].conceptoRecaudo").value("02"))
                .andExpect(jsonPath("$.rips.usuarios[0].serviciosTecnologias.consultas[0].valorPagoModerador").value(35000))
                .andExpect(jsonPath("$.rips.usuarios[0].serviciosTecnologias.consultas[0].numFEVPagoModerador")
                        .value(org.hamcrest.Matchers.startsWith("RIPA")));
    }

    @Test
    void saysWhatIsMissingAndRefusesInvoicesWithoutRips() throws Exception {
        anActiveResolution("RIPB");
        Episode episode = outpatient("COVERED");
        String sale = confirmedSale(episode);
        String copayment = copaymentCollected(episode, "35000");
        String draft = JsonPath.read(as("BILLING", post(INVOICES), drafting(episode, sale))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.uuid");

        as("BILLING", get(INVOICES + "/" + draft + "/rips"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVOICE_NOT_ISSUED"));
        as("BILLING", get(INVOICES + "/" + copayment + "/rips"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RIPS_NOT_APPLICABLE"));
        change("BILLING", post(INVOICES + "/" + draft + "/issuance"), 0, null).andExpect(status().isOk());
        as("BILLING", get(INVOICES + "/" + draft + "/rips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.gaps").value(org.hamcrest.Matchers.hasItem(
                        "Falta el diagnóstico principal de la atención (historia clínica)")));
        as("RECEPTIONIST", get(INVOICES + "/" + draft + "/rips")).andExpect(status().isForbidden());
    }
}
