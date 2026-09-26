package com.ClinicaDeYmid.contracting_service;

import com.ClinicaDeYmid.commons.security.testing.SecurityTestTokens;
import com.ClinicaDeYmid.contracting_service.support.JwtTestTokens;
import com.ClinicaDeYmid.contracting_service.support.MySqlTestContainer;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainer.class)
class ContractApiIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(500);

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
    }

    @Test
    void negotiatesAContractFromDraftToInForce() throws Exception {
        String payer = registerPayer();
        String tariffVersion = publishedTariffVersion();
        String contract = draftContract(payer, "EVENT");

        stepUp(put("/api/v1/contracts/" + contract + "/tariff-terms"), 0L,
                "{\"tariffVersionUuid\":\"" + tariffVersion + "\",\"factor\":1.3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tariffTerms.factor").value(1.3))
                .andExpect(jsonPath("$.status.billable").value(false));

        stepUp(post("/api/v1/contracts/" + contract + "/activation"), 1L, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("ACTIVE"))
                .andExpect(jsonPath("$.status.billable").value(true));

        as("BILLING", get("/api/v1/contracts?payer=" + payer + "&inForceOn=2026-03-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].uuid").value(contract))
                .andExpect(jsonPath("$[0].payerNit").isNotEmpty());
    }

    @Test
    void registersTheCoveragePlanAndTheCuconThatTheInvoiceReports() throws Exception {
        String contract = draftContract(registerPayer(), "CAPITATION");

        stepUp(put("/api/v1/contracts/" + contract + "/rips-registration"), 0L,
                "{\"coveragePlan\":\"UPC_SUBSIDIZED\",\"cucon\":\"XYZ\"}")
                .andExpect(status().isBadRequest());
        stepUp(put("/api/v1/contracts/" + contract + "/rips-registration"), 0L,
                "{\"coveragePlan\":\"UPC_SUBSIDIZED\",\"cucon\":\"" + "AB".repeat(32) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coveragePlan").value("UPC_SUBSIDIZED"))
                .andExpect(jsonPath("$.coveragePlanCode").value("17"))
                .andExpect(jsonPath("$.cucon").value("ab".repeat(32)));
        as("BILLING", get("/api/v1/contracts/" + contract))
                .andExpect(jsonPath("$.coveragePlanCode").value("17"));
        as("BILLING", put("/api/v1/contracts/" + contract + "/rips-registration").header("If-Match", "\"1\"")
                .contentType("application/json").content("{\"coveragePlan\":\"PRIVATE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void refusesToActivateWithoutTariffTerms() throws Exception {
        String contract = draftContract(registerPayer(), "EVENT");

        stepUp(post("/api/v1/contracts/" + contract + "/activation"), 0L, "{}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONTRACT_TARIFF_TERMS_MISSING"));
    }

    @Test
    void refusesTariffTermsForCapitation() throws Exception {
        String contract = draftContract(registerPayer(), "CAPITATION");

        stepUp(put("/api/v1/contracts/" + contract + "/tariff-terms"), 0L,
                "{\"tariffVersionUuid\":\"" + publishedTariffVersion() + "\",\"factor\":1.0}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONTRACT_TARIFF_NOT_APPLICABLE"));

        stepUp(post("/api/v1/contracts/" + contract + "/activation"), 0L, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("ACTIVE"));
    }

    @Test
    void replacesAnExceptionInsteadOfOverlappingIt() throws Exception {
        String contract = activeContract();

        stepUp(post("/api/v1/contracts/" + contract + "/tariff-exceptions"), null,
                "{\"cupsCode\":\"890201\",\"agreedPrice\":45000,\"reason\":\"Negociación puntual de consulta\",\"validFrom\":\"2026-01-01\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreedPrice").value(45000.00));

        stepUp(post("/api/v1/contracts/" + contract + "/tariff-exceptions"), null,
                "{\"cupsCode\":\"890201\",\"agreedPrice\":52000,\"reason\":\"Ajuste anual de la negociación\",\"validFrom\":\"2026-06-01\"}")
                .andExpect(status().isOk());

        as("BILLING", get("/api/v1/contracts/" + contract + "/tariff-exceptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].agreedPrice").value(52000.00))
                .andExpect(jsonPath("$[0].revokedFrom").doesNotExist())
                .andExpect(jsonPath("$[1].revokedFrom").value("2026-06-01"))
                .andExpect(jsonPath("$[1].revocationReason").isNotEmpty());
    }

    @Test
    void listsTheServicesThatNeedAuthorizationAndRefusesToRequireOneTwice() throws Exception {
        String contract = activeContract();

        as("CONTRACTING", post("/api/v1/contracts/" + contract + "/authorization-requirements")
                .content("{\"cupsCode\":\"871121\",\"validFrom\":\"2026-01-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cupsCode").value("871121"))
                .andExpect(jsonPath("$.registeredBy").isNotEmpty());
        as("CONTRACTING", post("/api/v1/contracts/" + contract + "/authorization-requirements")
                .content("{\"cupsCode\":\"871121\",\"validFrom\":\"2026-04-01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTHORIZATION_ALREADY_REQUIRED"));
        as("CONTRACTING", post("/api/v1/contracts/" + contract + "/authorization-requirements")
                .content("{\"cupsCode\":\"87A121\",\"validFrom\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest());
        as("BILLING", post("/api/v1/contracts/" + contract + "/authorization-requirements")
                .content("{\"cupsCode\":\"890201\",\"validFrom\":\"2026-01-01\"}"))
                .andExpect(status().isForbidden());

        as("BILLING", get("/api/v1/contracts/" + contract + "/authorization-requirements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cupsCode").value("871121"));
    }

    @Test
    void agreesAPackageWithItsIncludedServices() throws Exception {
        String contract = activeContract();

        String body = stepUp(post("/api/v1/contracts/" + contract + "/packages"), null,
                "{\"code\":\"PAQ-PARTO\",\"name\":\"Parto vaginal sin complicaciones\",\"price\":2500000,"
                        + "\"includedCodes\":[\"890201\",\"903841\"],\"validFrom\":\"2026-01-01\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.includedCodes.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        String packageUuid = JsonPath.read(body, "$.uuid");
        stepUp(post("/api/v1/contracts/packages/" + packageUuid + "/revocation"), null,
                "{\"from\":\"2026-07-01\",\"reason\":\"Se renegoció el paquete\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedFrom").value("2026-07-01"));
    }

    @Test
    void refusesAPackageWithASingleService() throws Exception {
        String contract = activeContract();

        stepUp(post("/api/v1/contracts/" + contract + "/packages"), null,
                "{\"code\":\"PAQ-UNO\",\"name\":\"Paquete incompleto\",\"price\":100000,"
                        + "\"includedCodes\":[\"890201\"],\"validFrom\":\"2026-01-01\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACTING_INVALID_DATA"));
    }

    @Test
    void demandsAFreshSecondFactorForEveryPriceDecision() throws Exception {
        String contract = draftContract(registerPayer(), "EVENT");
        UUID contracting = UUID.fromString(JwtTestTokens.USERS.get("CONTRACTING"));
        String staleToken = SecurityTestTokens.staff("CONTRACTING", contracting)
                .authenticatedAt(Instant.now().minus(Duration.ofMinutes(30))).bearer();

        mockMvc.perform(put("/api/v1/contracts/" + contract + "/tariff-terms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .header(HttpHeaders.AUTHORIZATION, staleToken)
                        .content("{\"tariffVersionUuid\":\"" + publishedTariffVersion() + "\",\"factor\":1.3}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
    }

    @Test
    void keepsTheContractNumberUniquePerPayer() throws Exception {
        String payer = registerPayer();
        String number = "CNT-" + SEQUENCE.incrementAndGet();
        as("CONTRACTING", post("/api/v1/contracts").content(draftBody(payer, number, "EVENT")))
                .andExpect(status().isCreated());

        as("CONTRACTING", post("/api/v1/contracts").content(draftBody(payer, number, "EVENT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_NUMBER_ALREADY_USED"));
    }

    @Test
    void refusesToNegotiateTermsAfterActivation() throws Exception {
        String contract = activeContract();

        stepUp(put("/api/v1/contracts/" + contract + "/tariff-terms"), 2L,
                "{\"tariffVersionUuid\":\"" + publishedTariffVersion() + "\",\"factor\":1.5}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_NEGOTIABLE"));
    }

    @Test
    void refusesContractsForAPayerThatIsNotActive() throws Exception {
        String payer = registerPayer();
        String body = as("CONTRACTING", get("/api/v1/payers/" + payer)).andReturn().getResponse().getContentAsString();
        long version = ((Number) JsonPath.read(body, "$.version")).longValue();
        mockMvc.perform(post("/api/v1/payers/" + payer + "/deactivation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.IF_MATCH, "\"" + version + "\"")
                        .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer("CONTRACTING"))
                        .content("{\"reason\":\"Liquidación de la entidad\"}"))
                .andExpect(status().isOk());

        as("CONTRACTING", post("/api/v1/contracts").content(draftBody(payer, "CNT-" + SEQUENCE.incrementAndGet(), "EVENT")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PAYER_NOT_ACTIVE"));
    }

    private String activeContract() throws Exception {
        String contract = draftContract(registerPayer(), "EVENT");
        stepUp(put("/api/v1/contracts/" + contract + "/tariff-terms"), 0L,
                "{\"tariffVersionUuid\":\"" + publishedTariffVersion() + "\",\"factor\":1.0}")
                .andExpect(status().isOk());
        stepUp(post("/api/v1/contracts/" + contract + "/activation"), 1L, "{}").andExpect(status().isOk());
        return contract;
    }

    private String draftContract(String payerUuid, String modality) throws Exception {
        String body = as("CONTRACTING", post("/api/v1/contracts")
                .content(draftBody(payerUuid, "CNT-" + SEQUENCE.incrementAndGet(), modality)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.uuid");
    }

    private static String draftBody(String payerUuid, String number, String modality) {
        return "{\"payerUuid\":\"" + payerUuid + "\",\"number\":\"" + number + "\",\"name\":\"Contrato de prueba\","
                + "\"modality\":\"" + modality + "\",\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\"}";
    }

    private String registerPayer() throws Exception {
        String nit = String.format("8%08d", SEQUENCE.incrementAndGet());
        String body = as("CONTRACTING", post("/api/v1/payers").content(
                "{\"identity\":{\"socialReason\":\"Pagador de prueba\",\"nit\":\"" + nit + "\",\"type\":\"EPS\"},"
                        + "\"contact\":{\"address\":\"Calle 1 # 2-3\",\"phone\":\"6011112222\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.uuid");
    }

    private String publishedTariffVersion() throws Exception {
        String manualBody = as("CONTRACTING", post("/api/v1/tariff-manuals")
                .content("{\"code\":\"CNT_" + SEQUENCE.incrementAndGet() + "\",\"name\":\"Manual de prueba\",\"unit\":\"COP\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String manual = JsonPath.read(manualBody, "$.uuid");

        String versionBody = as("CONTRACTING", post("/api/v1/tariff-manuals/" + manual + "/versions")
                .content("{\"label\":\"2026\",\"unitValue\":1,\"validFrom\":\"2026-01-01\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String version = JsonPath.read(versionBody, "$.uuid");

        as("CONTRACTING", post("/api/v1/tariff-manuals/versions/" + version + "/items")
                .content("{\"items\":[{\"cupsCode\":\"890201\",\"description\":\"Consulta\",\"value\":40000},"
                        + "{\"cupsCode\":\"903841\",\"description\":\"Hemograma\",\"value\":18000}]}"))
                .andExpect(status().isOk());
        stepUp(post("/api/v1/tariff-manuals/versions/" + version + "/activation"), 1L, "{}").andExpect(status().isOk());
        return version;
    }

    private ResultActions as(String role, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role)));
    }

    private ResultActions stepUp(MockHttpServletRequestBuilder request, Long version, String body) throws Exception {
        UUID contracting = UUID.fromString(JwtTestTokens.USERS.get("CONTRACTING"));
        MockHttpServletRequestBuilder prepared = request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, SecurityTestTokens.staff("CONTRACTING", contracting).bearer())
                .content(body);
        if (version != null) {
            prepared = prepared.header(HttpHeaders.IF_MATCH, "\"" + version + "\"");
        }
        return mockMvc.perform(prepared);
    }
}
