package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.application.CatalogueCommands;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.ServiceType;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReferences;
import com.ClinicaDeYmid.admissions_service.domain.practitioner.PractitionerReference;
import com.ClinicaDeYmid.admissions_service.domain.practitioner.PractitionerReferences;
import com.ClinicaDeYmid.admissions_service.support.JwtTestTokens;
import com.ClinicaDeYmid.admissions_service.support.PostgresTestContainer;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class AttendingPractitionerIT {

    private static final String EPISODES = "/api/v1/admissions/episodes";
    private static final AtomicInteger SEQUENCE = new AtomicInteger(600);

    @RegisterExtension
    static WireMockExtension practitionersService = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CatalogueCommands catalogue;

    @Autowired
    private PatientReferences patients;

    @Autowired
    private PractitionerReferences practitioners;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
        registry.add("spring.cloud.openfeign.client.config.practitioners-service.url",
                practitionersService::baseUrl);
    }

    @BeforeEach
    void resetStubs() {
        practitionersService.resetAll();
    }

    @Test
    void theEpisodeKeepsTheNameAndRegistrationOfWhoAttends() throws Exception {
        UUID practitioner = anActivePractitioner();
        String episode = anEpisode();

        change("RECEPTIONIST", post(EPISODES + "/" + episode + "/attending-practitioner"), 0,
                "{\"practitionerUuid\":\"" + practitioner + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attending.practitionerUuid").value(practitioner.toString()))
                .andExpect(jsonPath("$.attending.fullName").value("Laura Restrepo Gómez"))
                .andExpect(jsonPath("$.attending.registrationNumber").value("RM-12345"));
    }

    @Test
    void asksTheDirectoryWhenThePractitionerIsNotInTheLocalCopy() throws Exception {
        UUID practitioner = UUID.randomUUID();
        practitionersService.stubFor(get(urlPathMatching("/api/v1/practitioners/.*"))
                .willReturn(okJson(payload(practitioner, "ACTIVE"))));

        change("RECEPTIONIST", post(EPISODES + "/" + anEpisode() + "/attending-practitioner"), 0,
                "{\"practitionerUuid\":\"" + practitioner + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attending.registrationNumber").value("RM-99999"));

        practitionersService.verify(1,
                com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                        urlPathMatching("/api/v1/practitioners/.*")));
    }

    @Test
    void anUnknownPractitionerIsRefused() throws Exception {
        practitionersService.stubFor(get(urlPathMatching("/api/v1/practitioners/.*"))
                .willReturn(aResponse().withStatus(404)));

        change("RECEPTIONIST", post(EPISODES + "/" + anEpisode() + "/attending-practitioner"), 0,
                "{\"practitionerUuid\":\"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRACTITIONER_NOT_FOUND"));
    }

    @Test
    void anInactivePractitionerCannotTakeAnEpisode() throws Exception {
        UUID practitioner = UUID.randomUUID();
        practitioners.saveIfNewer(new PractitionerReference(practitioner, 1, "Carlos Mesa", "RM-5", null,
                "INACTIVE", null));

        change("RECEPTIONIST", post(EPISODES + "/" + anEpisode() + "/attending-practitioner"), 0,
                "{\"practitionerUuid\":\"" + practitioner + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRACTITIONER_NOT_AVAILABLE"));
    }

    @Test
    void theEpisodeKeepsTheRegistrationEvenIfTheDirectoryChangesLater() throws Exception {
        UUID practitioner = anActivePractitioner();
        String episode = anEpisode();
        change("RECEPTIONIST", post(EPISODES + "/" + episode + "/attending-practitioner"), 0,
                "{\"practitionerUuid\":\"" + practitioner + "\"}").andExpect(status().isOk());

        practitioners.saveIfNewer(new PractitionerReference(practitioner, 9, "Laura Restrepo Gómez",
                "RM-CAMBIADO", "Cardiología", "ACTIVE", null));

        as("BILLING", org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get(EPISODES + "/" + episode), null)
                .andExpect(jsonPath("$.attending.registrationNumber").value("RM-12345"));
    }

    @Test
    void aClosedEpisodeAcceptsNoPractitionerChange() throws Exception {
        UUID practitioner = anActivePractitioner();
        String episode = anEpisode();
        change("ADMIN", post(EPISODES + "/" + episode + "/cancellation"), 0,
                "{\"reason\":\"Se registró dos veces\"}").andExpect(status().isOk());

        change("RECEPTIONIST", post(EPISODES + "/" + episode + "/attending-practitioner"), 1,
                "{\"practitionerUuid\":\"" + practitioner + "\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void onlyReceptionAndNursingAssignTheAttendingPractitioner() throws Exception {
        UUID practitioner = anActivePractitioner();

        change("BILLING", post(EPISODES + "/" + anEpisode() + "/attending-practitioner"), 0,
                "{\"practitionerUuid\":\"" + practitioner + "\"}")
                .andExpect(status().isForbidden());
    }

    private UUID anActivePractitioner() {
        UUID uuid = UUID.randomUUID();
        practitioners.saveIfNewer(new PractitionerReference(uuid, 1, "Laura Restrepo Gómez", "RM-12345",
                "Medicina interna", "ACTIVE", UUID.randomUUID()));
        return uuid;
    }

    private static String payload(UUID uuid, String status) {
        return """
                {"uuid":"%s","version":2,"fullName":"Laura Restrepo Gómez",
                 "registration":{"number":"RM-99999"},"status":{"code":"%s"},
                 "specialties":[{"specialtyName":"Medicina interna","principal":true}]}""".formatted(uuid, status);
    }

    private String anEpisode() throws Exception {
        UUID patient = UUID.randomUUID();
        patients.saveIfNewer(new PatientReference.Registered(patient, 1,
                new PatientReference.Document("CEDULA_DE_CIUDADANIA", "60" + SEQUENCE.incrementAndGet()),
                "Ana María", "Restrepo Gómez", LocalDate.of(1990, 4, 12), PatientReference.Sex.FEMALE,
                PatientReference.Registered.Status.ACTIVE, null, "CONTRIBUTORY", null));
        String body = as("RECEPTIONIST", post(EPISODES), "{\"patientUuid\":\"" + patient
                + "\",\"configurationServiceUuid\":\"" + emergency() + "\",\"cause\":\"ILLNESS\"}")
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.uuid");
    }

    private UUID emergency() {
        int index = SEQUENCE.incrementAndGet();
        ServiceType type = catalogue.defineServiceType("Urgencias " + index, AdmissionKind.EMERGENCY);
        Location where = catalogue.defineLocation("Piso " + index);
        ConfigurationService configured = catalogue.configure(type.uuid(), where.uuid());
        return configured.uuid();
    }

    private ResultActions as(String role, MockHttpServletRequestBuilder request, String body) throws Exception {
        MockHttpServletRequestBuilder prepared = request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role));
        return mockMvc.perform(body == null ? prepared : prepared.content(body));
    }

    private ResultActions change(String role, MockHttpServletRequestBuilder request, long version, String body)
            throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.IF_MATCH, "\"" + version + "\"")
                .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role))
                .content(body));
    }
}
