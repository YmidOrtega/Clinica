package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.application.CatalogueCommands;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.ServiceType;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReferences;
import com.ClinicaDeYmid.admissions_service.support.JwtTestTokens;
import com.ClinicaDeYmid.admissions_service.support.StubbedServices;
import com.ClinicaDeYmid.admissions_service.support.TestSequence;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StepUpIT extends IntegrationTest {

    private static final String BASE = "/api/v1/admissions/episodes";

    @Autowired
    private CatalogueCommands catalogue;

    @Autowired
    private PatientReferences patients;

    @Test
    void aDeathDischargeDemandsASecondFactorVerifiedRecently() throws Exception {
        String episode = anActiveEpisode();
        String death = "{\"type\":\"DEATH\",\"occurredAt\":\"" + Instant.now().minusSeconds(600)
                + "\",\"certificateNumber\":\"CD-2026-9001\"}";

        changeWithToken(JwtTestTokens.bearerWithoutSecondFactor("DOCTOR"),
                post(BASE + "/" + episode + "/discharge"), 1, death)
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE));

        changeWithToken(JwtTestTokens.bearerAuthenticatedLongAgo("DOCTOR"),
                post(BASE + "/" + episode + "/discharge"), 1, death)
                .andExpect(status().isUnauthorized());

        change("DOCTOR", post(BASE + "/" + episode + "/discharge"), 1, death)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.discharge.type").value("DEATH"));
    }

    @Test
    void anEscapeDischargeDemandsItTooButAMedicalOneDoesNot() throws Exception {
        String escaped = anActiveEpisode();
        String discharged = anActiveEpisode();
        String escape = "{\"type\":\"ESCAPE\",\"noticedAt\":\"" + Instant.now().minusSeconds(300) + "\"}";

        changeWithToken(JwtTestTokens.bearerWithoutSecondFactor("DOCTOR"),
                post(BASE + "/" + escaped + "/discharge"), 1, escape)
                .andExpect(status().isUnauthorized());
        change("DOCTOR", post(BASE + "/" + escaped + "/discharge"), 1, escape)
                .andExpect(status().isOk());

        changeWithToken(JwtTestTokens.bearerWithoutSecondFactor("DOCTOR"),
                post(BASE + "/" + discharged + "/discharge"), 1, "{\"type\":\"MEDICAL\"}")
                .andExpect(status().isOk());
    }

    @Test
    void cancellingAnEpisodeAlwaysDemandsASecondFactor() throws Exception {
        String episode = anActiveEpisode();

        changeWithToken(JwtTestTokens.bearerWithoutSecondFactor("ADMIN"),
                post(BASE + "/" + episode + "/cancellation"), 1, "{\"reason\":\"Se registró dos veces\"}")
                .andExpect(status().isUnauthorized());

        change("ADMIN", post(BASE + "/" + episode + "/cancellation"), 1, "{\"reason\":\"Se registró dos veces\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("CANCELLED"));
    }

    @Test
    void admittingPastTheCoverageDemandsASecondFactorOnTopOfThePermission() throws Exception {
        UUID payer = UUID.randomUUID();
        StubbedServices.server().stubFor(get(urlPathEqualTo("/api/v1/contracts")).willReturn(okJson("[]")));
        UUID patient = aLocalPatient(payer);
        String body = "{\"patientUuid\":\"" + patient + "\",\"configurationServiceUuid\":\"" + inpatient()
                + "\",\"cause\":\"ILLNESS\",\"overrideCoverage\":true}";

        mockMvc.perform(post(BASE).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearerWithoutSecondFactor("ADMIN"))
                        .content(body))
                .andExpect(status().isUnauthorized());

        as("ADMIN", post(BASE), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coverage.status").value("NOT_COVERED"));
    }

    private String anActiveEpisode() throws Exception {
        UUID patient = aLocalPatient(null);
        String body = as("RECEPTIONIST", post(BASE), "{\"patientUuid\":\"" + patient
                + "\",\"configurationServiceUuid\":\"" + emergency() + "\",\"cause\":\"ILLNESS\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String uuid = JsonPath.read(body, "$.uuid");
        change("RECEPTIONIST", post(BASE + "/" + uuid + "/activation"), 0, null).andExpect(status().isOk());
        return uuid;
    }

    private UUID aLocalPatient(UUID payer) {
        UUID uuid = UUID.randomUUID();
        patients.saveIfNewer(new PatientReference.Registered(uuid, 1,
                new PatientReference.Document("CEDULA_DE_CIUDADANIA", "10" + TestSequence.next()),
                "Ana María", "Restrepo Gómez", LocalDate.of(1990, 4, 12), PatientReference.Sex.FEMALE,
                PatientReference.Registered.Status.ACTIVE, null, "CONTRIBUTORY",
                payer == null ? null : payer.toString()));
        return uuid;
    }

    private UUID emergency() {
        return configured("Urgencias step-up", AdmissionKind.EMERGENCY);
    }

    private UUID inpatient() {
        return configured("Hospitalización step-up", AdmissionKind.INPATIENT);
    }

    private UUID configured(String name, AdmissionKind kind) {
        int index = TestSequence.next();
        ServiceType type = catalogue.defineServiceType(name + " " + index, kind);
        Location where = catalogue.defineLocation("Sede step-up " + index);
        ConfigurationService service = catalogue.configure(type.uuid(), where.uuid());
        return service.uuid();
    }
}
