package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.application.CatalogueCommands;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.ServiceType;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReferences;
import com.ClinicaDeYmid.admissions_service.support.JwtTestTokens;
import com.ClinicaDeYmid.admissions_service.support.TestSequence;
import com.ClinicaDeYmid.admissions_service.support.StubbedServices;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthorizationApiIT extends IntegrationTest {

    private static final String BASE = "/api/v1/admissions";
    @Autowired
    private CatalogueCommands catalogue;

    @Autowired
    private PatientReferences patients;

    @Test
    void registersAnAuthorizationWithItsCopaymentAndValidity() throws Exception {
        String admission = anAdmission();

        as("RECEPTIONIST", post(BASE + "/episodes/" + admission + "/authorizations"), """
                {"number":"AUT-2026-0001","type":"EMERGENCY_SERVICES","authorizedBy":"Auditor del pagador",
                 "copayment":15000.50,"validFrom":"2026-09-01","validTo":"2026-12-31"}""")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.number").value("AUT-2026-0001"))
                .andExpect(jsonPath("$.copayment").value(15000.50))
                .andExpect(jsonPath("$.coversEverything").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void keepsTheAuthorizedPortfolioItems() throws Exception {
        String admission = anAdmission();
        UUID first = UUID.randomUUID();

        as("RECEPTIONIST", post(BASE + "/episodes/" + admission + "/authorizations"),
                "{\"number\":\"AUT-2026-0002\",\"type\":\"SPECIALIZED_SERVICES\",\"authorizedItems\":[\""
                        + first + "\",\"" + UUID.randomUUID() + "\"]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorizedItems.length()").value(2))
                .andExpect(jsonPath("$.coversEverything").value(false));
    }

    @Test
    void refusesTwoAuthorizationsWithTheSameNumberOnOneEpisode() throws Exception {
        String admission = anAdmission();
        String body = "{\"number\":\"AUT-2026-0003\",\"type\":\"HOSPITALIZATION\"}";

        as("RECEPTIONIST", post(BASE + "/episodes/" + admission + "/authorizations"), body)
                .andExpect(status().isCreated());
        as("RECEPTIONIST", post(BASE + "/episodes/" + admission + "/authorizations"), body)
                .andExpect(status().isConflict());
    }

    @Test
    void theSameNumberIsAcceptedOnAnotherEpisode() throws Exception {
        String body = "{\"number\":\"AUT-SHARED\",\"type\":\"HOSPITALIZATION\"}";

        as("RECEPTIONIST", post(BASE + "/episodes/" + anAdmission() + "/authorizations"), body)
                .andExpect(status().isCreated());
        as("RECEPTIONIST", post(BASE + "/episodes/" + anAdmission() + "/authorizations"), body)
                .andExpect(status().isCreated());
    }

    @Test
    void revokesAnAuthorizationKeepingItInTheList() throws Exception {
        String admission = anAdmission();
        String uuid = JsonPath.read(as("RECEPTIONIST", post(BASE + "/episodes/" + admission + "/authorizations"),
                        "{\"number\":\"AUT-2026-0004\",\"type\":\"MEDICATIONS\"}")
                .andReturn().getResponse().getContentAsString(), "$.uuid");

        change("RECEPTIONIST", post(BASE + "/authorizations/" + uuid + "/revocation"), 0,
                "{\"reason\":\"El pagador la anuló por duplicada\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.statusReason").value("El pagador la anuló por duplicada"));

        as("BILLING", get(BASE + "/episodes/" + admission + "/authorizations"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("REVOKED"));
    }

    @Test
    void revokingDemandsAReasonAndTheCurrentVersion() throws Exception {
        String uuid = JsonPath.read(as("RECEPTIONIST", post(BASE + "/episodes/" + anAdmission() + "/authorizations"),
                        "{\"number\":\"AUT-2026-0005\",\"type\":\"TRANSPORT_SERVICES\"}")
                .andReturn().getResponse().getContentAsString(), "$.uuid");

        change("RECEPTIONIST", post(BASE + "/authorizations/" + uuid + "/revocation"), 0, "{\"reason\":\" \"}")
                .andExpect(status().isBadRequest());
        change("RECEPTIONIST", post(BASE + "/authorizations/" + uuid + "/revocation"), 9, "{\"reason\":\"Tarde\"}")
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void aClosedEpisodeAcceptsNoMoreAuthorizations() throws Exception {
        String admission = anAdmission();
        change("ADMIN", post("/api/v1/admissions/episodes/" + admission + "/cancellation"), 0,
                "{\"reason\":\"Se registró dos veces\"}").andExpect(status().isOk());

        as("RECEPTIONIST", post(BASE + "/episodes/" + admission + "/authorizations"),
                "{\"number\":\"AUT-LATE\",\"type\":\"HOSPITALIZATION\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void refusesANegativeCopayment() throws Exception {
        as("RECEPTIONIST", post(BASE + "/episodes/" + anAdmission() + "/authorizations"),
                "{\"number\":\"AUT-NEG\",\"type\":\"MEDICATIONS\",\"copayment\":-1}")
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOCTOR", "MEDICAL_RECORDS", "BILLING"})
    void onlyReceptionAndNursingRegisterAuthorizationsButEveryoneReadsThem(String role) throws Exception {
        String admission = anAdmission();

        as(role, post(BASE + "/episodes/" + admission + "/authorizations"),
                "{\"number\":\"AUT-X\",\"type\":\"HOSPITALIZATION\"}")
                .andExpect(status().isForbidden());
        as(role, get(BASE + "/episodes/" + admission + "/authorizations"), null)
                .andExpect(status().isOk());
    }

    private String anAdmission() throws Exception {
        UUID patient = UUID.randomUUID();
        patients.saveIfNewer(new PatientReference.Registered(patient, 1,
                new PatientReference.Document("CEDULA_DE_CIUDADANIA", "40" + TestSequence.next()),
                "Ana María", "Restrepo Gómez", LocalDate.of(1990, 4, 12), PatientReference.Sex.FEMALE,
                PatientReference.Registered.Status.ACTIVE, null, "CONTRIBUTORY", null));
        String body = as("RECEPTIONIST", post("/api/v1/admissions/episodes"),
                "{\"patientUuid\":\"" + patient + "\",\"configurationServiceUuid\":\"" + emergency()
                        + "\",\"cause\":\"ILLNESS\"}").andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.uuid");
    }

    private UUID emergency() {
        int index = TestSequence.next();
        ServiceType type = catalogue.defineServiceType("Urgencias " + index, AdmissionKind.EMERGENCY);
        Location where = catalogue.defineLocation("Piso " + index);
        ConfigurationService configured = catalogue.configure(type.uuid(), where.uuid());
        return configured.uuid();
    }

}
