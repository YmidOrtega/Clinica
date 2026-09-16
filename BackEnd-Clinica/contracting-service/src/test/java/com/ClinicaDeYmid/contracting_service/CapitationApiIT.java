package com.ClinicaDeYmid.contracting_service;

import com.ClinicaDeYmid.commons.security.testing.SecurityTestTokens;
import com.ClinicaDeYmid.contracting_service.support.JwtTestTokens;
import com.ClinicaDeYmid.contracting_service.support.MySqlTestContainer;
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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainer.class)
class CapitationApiIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(900);

    @RegisterExtension
    static WireMockExtension patientService = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
        registry.add("spring.cloud.openfeign.client.config.patient-service.url", patientService::baseUrl);
        registry.add("clinica.contracting.patient-directory.ttl", () -> "0s");
    }

    @BeforeEach
    void resetStubs() {
        patientService.resetAll();
    }

    @Test
    void agreesCapitationAndLoadsItsPopulation() throws Exception {
        String contract = activeContract("CAPITATION");
        patientRegistered();

        stepUp(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/contracts/" + contract + "/capitation-agreement"),
                "{\"perCapitaValue\":38500.50,\"periodicity\":\"MONTHLY\","
                        + "\"technicalNote\":\"Nota técnica anexa al contrato, versión 3\",\"validFrom\":\"2026-01-01\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perCapitaValue").value(38500.50))
                .andExpect(jsonPath("$.periodicityLabel").value("Mensual"))
                .andExpect(jsonPath("$.modality").value("CAPITATION"));

        as("CONTRACTING", org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/contracts/" + contract + "/capitated-members/imports?period=2026-03")
                .content(population()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.matched").value(2))
                .andExpect(jsonPath("$.unverified").value(0));

        as("BILLING", get("/api/v1/contracts/" + contract + "/capitated-members?period=2026-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].verification").value("MATCHED"))
                .andExpect(jsonPath("$.content[0].patientUuid").isNotEmpty());
    }

    @Test
    void loadingTheSamePopulationTwiceOnlyUpdatesWhatChanged() throws Exception {
        String contract = activeContract("CAPITATION");
        String document = String.format("10%08d", SEQUENCE.incrementAndGet());
        patientRegistered();
        importPopulation(contract, population(document));

        as("CONTRACTING", org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/contracts/" + contract + "/capitated-members/imports?period=2026-03")
                .content(population(document).replace("Ana María Rojas", "Ana María Rojas Pérez")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.unchanged").value(1));
    }

    @Test
    void leavesMembersUnverifiedWhenTheRegistryIsDownAndRetriesLater() throws Exception {
        String contract = activeContract("CAPITATION");
        patientService.stubFor(post(urlPathEqualTo("/api/v1/patients/search"))
                .willReturn(aResponse().withStatus(503)));

        as("CONTRACTING", org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/contracts/" + contract + "/capitated-members/imports?period=2026-03")
                .content(population()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.unverified").value(2))
                .andExpect(jsonPath("$.matched").value(0));

        as("BILLING", get("/api/v1/contracts/" + contract + "/capitated-members?period=2026-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].verification").value("UNVERIFIED"));

        patientRegistered();
        as("CONTRACTING", org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/contracts/" + contract + "/capitated-members/verification?period=2026-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(2))
                .andExpect(jsonPath("$.unverified").value(0));
    }

    @Test
    void marksAsUnmatchedSomebodyThatIsNotAPatientYet() throws Exception {
        String contract = activeContract("CAPITATION");
        patientService.stubFor(post(urlPathEqualTo("/api/v1/patients/search"))
                .willReturn(okJson("{\"content\":[]}")));

        as("CONTRACTING", org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/contracts/" + contract + "/capitated-members/imports?period=2026-03")
                .content(population()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unmatched").value(2));
    }

    @Test
    void answersWhetherAPersonIsCapitatedOnADate() throws Exception {
        String contract = activeContract("CAPITATION");
        String document = String.format("10%08d", SEQUENCE.incrementAndGet());
        patientRegistered();
        importPopulation(contract, population(document));

        as("RECEPTIONIST", get("/api/v1/capitated-members/coverage?documentType=CC&documentNumber=" + document + "&on=2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].contractUuid").value(contract))
                .andExpect(jsonPath("$[0].payerName").isNotEmpty());

        as("RECEPTIONIST", get("/api/v1/capitated-members/coverage?documentType=CC&documentNumber=" + document + "&on=2026-05-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void refusesCapitatedPopulationOnAContractPaidPerEvent() throws Exception {
        String contract = activeContract("EVENT");

        as("CONTRACTING", org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/contracts/" + contract + "/capitated-members/imports?period=2026-03")
                .content(population()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONTRACT_CAPITATION_NOT_APPLICABLE"));
    }

    @Test
    void refusesACapitationAgreementOnABudgetContract() throws Exception {
        String contract = activeContract("GLOBAL_BUDGET");

        stepUp(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/contracts/" + contract + "/capitation-agreement"),
                "{\"perCapitaValue\":38500,\"periodicity\":\"MONTHLY\","
                        + "\"technicalNote\":\"Nota técnica anexa al contrato\",\"validFrom\":\"2026-01-01\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONTRACT_FUNDING_NOT_APPLICABLE"));

        stepUp(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/contracts/" + contract + "/budget-agreement"),
                "{\"budgetCeiling\":1200000000,\"periodicity\":\"MONTHLY\","
                        + "\"technicalNote\":\"Presupuesto global prospectivo anual\",\"validFrom\":\"2026-01-01\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetCeiling").value(1200000000));
    }

    private void importPopulation(String contract, String body) throws Exception {
        as("CONTRACTING", org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/contracts/" + contract + "/capitated-members/imports?period=2026-03")
                .content(body))
                .andExpect(status().isOk());
    }

    private void patientRegistered() {
        patientService.stubFor(post(urlPathEqualTo("/api/v1/patients/search"))
                .willReturn(okJson("{\"content\":[{\"uuid\":\"" + UUID.randomUUID() + "\"}]}")));
    }

    private String activeContract(String modality) throws Exception {
        String payer = registerPayer();
        String contract = JsonPath.read(as("CONTRACTING", org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/contracts")
                        .content("{\"payerUuid\":\"" + payer + "\",\"number\":\"CAP-" + SEQUENCE.incrementAndGet()
                                + "\",\"name\":\"Contrato de capitación\",\"modality\":\"" + modality
                                + "\",\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.uuid");
        if (modality.equals("EVENT")) {
            return contract;
        }
        stepUpWithVersion(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/contracts/" + contract + "/activation"), 0L)
                .andExpect(status().isOk());
        return contract;
    }

    private String registerPayer() throws Exception {
        String nit = String.format("7%08d", SEQUENCE.incrementAndGet());
        return JsonPath.read(as("CONTRACTING", org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/payers")
                        .content("{\"identity\":{\"socialReason\":\"Pagador capitado\",\"nit\":\"" + nit + "\",\"type\":\"EPS\"},"
                                + "\"contact\":{\"address\":\"Calle 1 # 2-3\",\"phone\":\"6011112222\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.uuid");
    }

    private static String population() {
        return population(String.format("10%08d", SEQUENCE.incrementAndGet()));
    }

    private static String population(String documentNumber) {
        return "{\"members\":["
                + "{\"documentType\":\"CC\",\"documentNumber\":\"" + documentNumber + "\",\"fullName\":\"Ana María Rojas\"},"
                + "{\"documentType\":\"CC\",\"documentNumber\":\"" + documentNumber + "1\",\"fullName\":\"Carlos Pérez\"}]}";
    }

    private ResultActions as(String role, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role)));
    }

    private ResultActions stepUp(MockHttpServletRequestBuilder request, String body) throws Exception {
        UUID contracting = UUID.fromString(JwtTestTokens.USERS.get("CONTRACTING"));
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, SecurityTestTokens.staff("CONTRACTING", contracting).bearer())
                .content(body));
    }

    private ResultActions stepUpWithVersion(MockHttpServletRequestBuilder request, long version) throws Exception {
        UUID contracting = UUID.fromString(JwtTestTokens.USERS.get("CONTRACTING"));
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.IF_MATCH, "\"" + version + "\"")
                .header(HttpHeaders.AUTHORIZATION, SecurityTestTokens.staff("CONTRACTING", contracting).bearer())
                .content("{}"));
    }
}
