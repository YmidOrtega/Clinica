package com.ClinicaDeYmid.patient_service;

import com.ClinicaDeYmid.patient_service.support.JwtTestTokens;
import com.ClinicaDeYmid.patient_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.patient_service.support.PatientJson;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainer.class)
class PatientApiIT {

    private static final String PROVIDERS_PATH = "/api/v1/billing-service/health-providers/";

    @RegisterExtension
    static WireMockExtension clientsService = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("clinica.security.jwt.public-key", JwtTestTokens::publicKeyBase64);
        registry.add("spring.cloud.openfeign.client.config.clients-service.url", clientsService::baseUrl);
        registry.add("eureka.client.enabled", () -> false);
        registry.add("clinica.patient.health-providers.fresh-ttl", () -> "0s");
    }

    @Test
    void registersAPatientAndReturnsItsVersion() throws Exception {
        providerExists(PatientJson.PROVIDER_NIT);

        as("RECEPTIONIST", post("/api/v1/patients").content(PatientJson.registration(PatientJson.uniqueCedula(), PatientJson.PROVIDER_NIT)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/v1/patients/")))
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.status.code").value("ACTIVE"))
                .andExpect(jsonPath("$.demographics.firstNames").value("Ana María"))
                .andExpect(jsonPath("$.audit.createdBy").value(JwtTestTokens.USERS.get("RECEPTIONIST")));
    }

    @Test
    void registersUninsuredPatientsWithoutCallingClientsService() throws Exception {
        as("ADMIN", post("/api/v1/patients").content(PatientJson.uninsuredRegistration(PatientJson.uniqueCedula())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.affiliation.regime").value("UNINSURED"));

        clientsService.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void rejectsUnknownHealthProviders() throws Exception {
        String nit = "800000001-1";
        clientsService.stubFor(get(urlPathEqualTo(PROVIDERS_PATH + nit)).willReturn(aResponse().withStatus(404)));

        as("RECEPTIONIST", post("/api/v1/patients").content(PatientJson.registration(PatientJson.uniqueCedula(), nit)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("HEALTH_PROVIDER_NOT_FOUND"));
    }

    @Test
    void answers503WhenTheHealthProviderCannotBeVerified() throws Exception {
        String nit = "800000002-2";
        clientsService.stubFor(get(urlPathEqualTo(PROVIDERS_PATH + nit)).willReturn(aResponse().withStatus(500)));

        as("RECEPTIONIST", post("/api/v1/patients").content(PatientJson.registration(PatientJson.uniqueCedula(), nit)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.code").value("HEALTH_PROVIDER_UNAVAILABLE"));
    }

    @Test
    void servesPatientsWithTheLastKnownHealthProviderWhenClientsServiceFails() throws Exception {
        String nit = "800000003-3";
        providerExists(nit);
        String uuid = register(PatientJson.registration(PatientJson.uniqueCedula(), nit));

        as("DOCTOR", fetch("/api/v1/patients/" + uuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthProvider.availability").value("AVAILABLE"));

        clientsService.stubFor(get(urlPathEqualTo(PROVIDERS_PATH + nit)).willReturn(aResponse().withStatus(503)));

        as("NURSE", fetch("/api/v1/patients/" + uuid))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.uuid").value(uuid))
                .andExpect(jsonPath("$.healthProvider.availability").value("AVAILABLE"))
                .andExpect(jsonPath("$.healthProvider.name").value("Salud Total EPS S.A."));
    }

    @Test
    void answersWithinTheTimeoutWhenClientsServiceIsSlow() throws Exception {
        String nit = "800000004-4";
        providerExists(nit);
        String uuid = register(PatientJson.registration(PatientJson.uniqueCedula(), nit));
        clientsService.stubFor(get(urlPathEqualTo(PROVIDERS_PATH + nit)).willReturn(aResponse().withFixedDelay(3000)));

        as("DOCTOR", fetch("/api/v1/patients/" + uuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthProvider.availability").value("AVAILABLE"));
    }

    @Test
    void rejectsDuplicatedDocuments() throws Exception {
        providerExists(PatientJson.PROVIDER_NIT);
        String document = PatientJson.uniqueCedula();
        register(PatientJson.registration(document, PatientJson.PROVIDER_NIT));

        as("RECEPTIONIST", post("/api/v1/patients").content(PatientJson.registration(document, PatientJson.PROVIDER_NIT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PATIENT_DOCUMENT_ALREADY_REGISTERED"));
    }

    @Test
    void reportsInvalidAndIncompleteRequests() throws Exception {
        as("RECEPTIONIST", post("/api/v1/patients").content(PatientJson.registration("12AB", PatientJson.PROVIDER_NIT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PATIENT_INVALID_DATA"))
                .andExpect(content().string(not(containsString("12AB"))));

        as("RECEPTIONIST", post("/api/v1/patients").content("{\"document\": {\"type\": \"PASAPORTE\", \"number\": \"AB123\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[?(@.field == 'demographics')]").exists());
    }

    @Test
    void enforcesAuthenticationAndRoles() throws Exception {
        mockMvc.perform(fetch("/api/v1/patients/" + java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        as("NURSE", post("/api/v1/patients").content(PatientJson.uninsuredRegistration(PatientJson.uniqueCedula())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void requiresTheCurrentVersionToModifyAPatient() throws Exception {
        String uuid = register(PatientJson.uninsuredRegistration(PatientJson.uniqueCedula()));
        String contact = "{\"contact\": {\"mobile\": \"3150001122\"}}";

        as("RECEPTIONIST", put("/api/v1/patients/" + uuid + "/contact").content(contact))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PATIENT_VERSION_REQUIRED"));

        as("RECEPTIONIST", put("/api/v1/patients/" + uuid + "/contact").header(HttpHeaders.IF_MATCH, "\"7\"").content(contact))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("PATIENT_VERSION_MISMATCH"));

        as("RECEPTIONIST", put("/api/v1/patients/" + uuid + "/contact").header(HttpHeaders.IF_MATCH, "\"0\"").content(contact))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.contact.mobile").value("3150001122"));

        as("RECEPTIONIST", put("/api/v1/patients/" + uuid + "/contact").header(HttpHeaders.IF_MATCH, "\"0\"").content(contact))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void appliesStatusRulesAndRoles() throws Exception {
        String uuid = register(PatientJson.uninsuredRegistration(PatientJson.uniqueCedula()));
        String reason = "{\"reason\": \"Registro duplicado\"}";

        as("RECEPTIONIST", post("/api/v1/patients/" + uuid + "/deactivation").header(HttpHeaders.IF_MATCH, "\"0\"").content(reason))
                .andExpect(status().isForbidden());

        as("ADMIN", post("/api/v1/patients/" + uuid + "/deactivation").header(HttpHeaders.IF_MATCH, "\"0\"").content(reason))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("INACTIVE"))
                .andExpect(jsonPath("$.status.reason").value("Registro duplicado"));

        as("RECEPTIONIST", put("/api/v1/patients/" + uuid + "/residence").header(HttpHeaders.IF_MATCH, "\"1\"")
                .content("{\"department\": \"Santander\", \"municipality\": \"Piedecuesta\", \"zone\": \"URBAN\", \"address\": \"Cra 5\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_ACTIVE"));
    }

    @Test
    void exposesTheChangeHistoryOnlyToAdministrators() throws Exception {
        String uuid = register(PatientJson.uninsuredRegistration(PatientJson.uniqueCedula()));
        as("RECEPTIONIST", put("/api/v1/patients/" + uuid + "/contact").header(HttpHeaders.IF_MATCH, "\"0\"")
                .content("{\"contact\": {\"mobile\": \"3150001122\"}}"))
                .andExpect(status().isOk());

        as("DOCTOR", fetch("/api/v1/patients/" + uuid + "/history"))
                .andExpect(status().isForbidden());

        as("ADMIN", fetch("/api/v1/patients/" + uuid + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].changeType").value("CREATED"))
                .andExpect(jsonPath("$[1].changeType").value("UPDATED"))
                .andExpect(jsonPath("$[1].revisedBy").value(JwtTestTokens.USERS.get("RECEPTIONIST")))
                .andExpect(jsonPath("$[1].state.contact.mobile").value("3150001122"));
    }

    @Test
    void searchesByDocumentOrNameWithoutExposingThemInTheUrl() throws Exception {
        String document = PatientJson.uniqueCedula();
        register(PatientJson.uninsuredRegistration(document));

        as("DOCTOR", post("/api/v1/patients/search")
                .content("{\"document\": {\"type\": \"CEDULA_DE_CIUDADANIA\", \"number\": \"" + document + "\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].document.number").value(document))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        as("DOCTOR", post("/api/v1/patients/search").content("{\"name\": {\"lastNames\": \"pere\", \"firstNames\": \"luis\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.document.number == '" + document + "')]").exists());

        as("DOCTOR", post("/api/v1/patients/search").content("{}"))
                .andExpect(status().isBadRequest());
    }

    private String register(String body) throws Exception {
        String response = as("ADMIN", post("/api/v1/patients").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.uuid");
    }

    private void providerExists(String nit) {
        clientsService.stubFor(get(urlPathEqualTo(PROVIDERS_PATH + nit)).willReturn(okJson(PatientJson.healthProvider(nit))));
    }

    private ResultActions as(String role, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request
                .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role))
                .contentType(MediaType.APPLICATION_JSON));
    }

    private static MockHttpServletRequestBuilder fetch(String path) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path);
    }
}
