package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.application.CatalogueCommands;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.ServiceType;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReferences;
import com.ClinicaDeYmid.admissions_service.support.TestSequence;
import com.ClinicaDeYmid.commons.documents.Documents;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReceiptApiIT extends IntegrationTest {

    private static final String BASE = "/api/v1/admissions";
    private static final String EPISODES = BASE + "/episodes";

    @Autowired
    private CatalogueCommands catalogue;

    @Autowired
    private PatientReferences patients;

    @Test
    void issuesASealedReceiptAndVerifiesTheVerySameDocument() throws Exception {
        String episode = anEpisode();

        byte[] document = as("RECEPTIONIST", post(EPISODES + "/" + episode + "/receipt"), null)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().exists("X-Receipt-Id"))
                .andReturn().getResponse().getContentAsByteArray();
        String receiptId = as("RECEPTIONIST", post(EPISODES + "/" + episode + "/receipt"), null)
                .andReturn().getResponse().getHeader("X-Receipt-Id");

        assertThat(new String(document, 0, 5)).isEqualTo("%PDF-");

        String record = as("BILLING", get(BASE + "/receipts/" + receiptId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admissionUuid").value(episode))
                .andExpect(jsonPath("$.keyId").isNotEmpty())
                .andExpect(jsonPath("$.seal").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(record, "$.documentSha256")).hasSize(64);
    }

    @Test
    void tellsApartTheEmittedDocumentFromATamperedOne() throws Exception {
        String episode = anEpisode();
        var response = as("RECEPTIONIST", post(EPISODES + "/" + episode + "/receipt"), null)
                .andReturn().getResponse();
        byte[] document = response.getContentAsByteArray();
        String receiptId = response.getHeader("X-Receipt-Id");

        mockMvc.perform(multipart(BASE + "/receipts/" + receiptId + "/verification")
                        .file(new MockMultipartFile("document", "receipt.pdf",
                                MediaType.APPLICATION_PDF_VALUE, document))
                        .header("Authorization", bearer("BILLING")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentMatches").value(true))
                .andExpect(jsonPath("$.sealValid").value(true))
                .andExpect(jsonPath("$.authentic").value(true));

        byte[] tampered = document.clone();
        tampered[tampered.length - 1] = (byte) (tampered[tampered.length - 1] ^ 0x01);
        mockMvc.perform(multipart(BASE + "/receipts/" + receiptId + "/verification")
                        .file(new MockMultipartFile("document", "receipt.pdf",
                                MediaType.APPLICATION_PDF_VALUE, tampered))
                        .header("Authorization", bearer("BILLING")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentMatches").value(false))
                .andExpect(jsonPath("$.authentic").value(false));
    }

    @Test
    void anybodyCanCheckAFingerprintWithoutCredentialsAndWithoutLearningAnything() throws Exception {
        String episode = anEpisode();
        var response = as("RECEPTIONIST", post(EPISODES + "/" + episode + "/receipt"), null)
                .andReturn().getResponse();
        String number = JsonPath.read(as("BILLING", get(EPISODES + "/" + episode), null)
                .andReturn().getResponse().getContentAsString(), "$.number");
        String fingerprint = Documents.sha256(response.getContentAsByteArray());

        mockMvc.perform(post(BASE + "/receipts/verification").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":\"" + number + "\",\"sha256\":\"" + fingerprint + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authentic").value(true))
                .andExpect(jsonPath("$.number").doesNotExist());

        mockMvc.perform(post(BASE + "/receipts/verification").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":\"" + number + "\",\"sha256\":\"" + "0".repeat(64) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authentic").value(false));

        mockMvc.perform(post(BASE + "/receipts/verification").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":\"" + number + "\",\"sha256\":\"not-a-fingerprint\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stopsSomebodyFishingForFingerprintsOnThePublicDoor() throws Exception {
        String body = "{\"number\":\"ADM-2026-999999\",\"sha256\":\"" + "a".repeat(64) + "\"}";
        int refusedAt = 0;

        for (int attempt = 1; attempt <= 25 && refusedAt == 0; attempt++) {
            int status = mockMvc.perform(post(BASE + "/receipts/verification")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn().getResponse().getStatus();
            if (status == 429) {
                refusedAt = attempt;
            }
        }

        assertThat(refusedAt).as("the public door stops answering after a burst").isBetween(1, 25);
        mockMvc.perform(post(BASE + "/receipts/verification").contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_CHECKS"));
    }

    @Test
    void publishesTheKeysThatSealTheReceipts() throws Exception {
        mockMvc.perform(get(BASE + "/seal-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*").isNotEmpty());
    }

    private String anEpisode() throws Exception {
        UUID patient = UUID.randomUUID();
        patients.saveIfNewer(new PatientReference.Registered(patient, 1,
                new PatientReference.Document("CEDULA_DE_CIUDADANIA", "30" + TestSequence.next()),
                "Ana María", "Restrepo Gómez", LocalDate.of(1990, 4, 12), PatientReference.Sex.FEMALE,
                PatientReference.Registered.Status.ACTIVE, null, "CONTRIBUTORY", null));
        int index = TestSequence.next();
        ServiceType type = catalogue.defineServiceType("Urgencias comprobante " + index, AdmissionKind.EMERGENCY);
        Location where = catalogue.defineLocation("Sede comprobante " + index);
        ConfigurationService service = catalogue.configure(type.uuid(), where.uuid());
        String body = as("RECEPTIONIST", post(EPISODES), "{\"patientUuid\":\"" + patient
                + "\",\"configurationServiceUuid\":\"" + service.uuid() + "\",\"cause\":\"ILLNESS\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.uuid");
    }
}
