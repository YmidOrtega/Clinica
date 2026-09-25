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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainer.class)
class PricingApiIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(300);

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
    }

    @Test
    void appliesTheManualWithTheAgreedFactor() throws Exception {
        String contract = activeEventContract("1.3");

        as("BILLING", post("/api/v1/price-quotes").content(quoteFor(contract, "890201", 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[0].origin").value("TARIFF_MANUAL"))
                .andExpect(jsonPath("$.services[0].unitPrice").value(52000.00))
                .andExpect(jsonPath("$.services[0].lineTotal").value(104000.00))
                .andExpect(jsonPath("$.services[0].billablePerService").value(true))
                .andExpect(jsonPath("$.tariff.factor").value(1.3))
                .andExpect(jsonPath("$.tariff.manualCode").isNotEmpty())
                .andExpect(jsonPath("$.total").value(104000.00));
    }

    @Test
    void convertsTariffsExpressedInMinimumWages() throws Exception {
        String manual = manualWithVersion("SMLDV", "47450.00", "2.5", "1.25");
        String contract = activeContract(manual, "1.0");

        as("BILLING", post("/api/v1/price-quotes").content(quoteFor(contract, "890201", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[0].unitPrice").value(118625.00))
                .andExpect(jsonPath("$.tariff.unit").value("SMLDV"))
                .andExpect(jsonPath("$.tariff.unitValue").value(47450.0));
    }

    @Test
    void anExceptionBeatsTheManual() throws Exception {
        String contract = activeEventContract("1.3");
        stepUp(post("/api/v1/contracts/" + contract + "/tariff-exceptions"),
                "{\"cupsCode\":\"890201\",\"agreedPrice\":45000,\"reason\":\"Negociación puntual de consulta\",\"validFrom\":\"2026-01-01\"}")
                .andExpect(status().isOk());

        as("BILLING", post("/api/v1/price-quotes").content(quoteFor(contract, "890201", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[0].origin").value("CONTRACT_EXCEPTION"))
                .andExpect(jsonPath("$.services[0].unitPrice").value(45000.00));
    }

    @Test
    void aPackageBeatsEverythingAndIsChargedOnce() throws Exception {
        String contract = activeEventContract("1.0");
        stepUp(post("/api/v1/contracts/" + contract + "/packages"),
                "{\"code\":\"PAQ-" + SEQUENCE.incrementAndGet() + "\",\"name\":\"Paquete de atención\",\"price\":150000,"
                        + "\"includedCodes\":[\"890201\",\"903841\"],\"validFrom\":\"2026-01-01\"}")
                .andExpect(status().isOk());

        as("BILLING", post("/api/v1/price-quotes").content(
                "{\"contractUuid\":\"" + contract + "\",\"on\":\"2026-03-01\",\"services\":["
                        + "{\"cupsCode\":\"890201\",\"quantity\":1},{\"cupsCode\":\"903841\",\"quantity\":2}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[0].origin").value("PACKAGE"))
                .andExpect(jsonPath("$.services[0].unitPrice").value(0))
                .andExpect(jsonPath("$.services[1].origin").value("PACKAGE"))
                .andExpect(jsonPath("$.packages.length()").value(1))
                .andExpect(jsonPath("$.packages[0].price").value(150000.00))
                .andExpect(jsonPath("$.total").value(150000.00));
    }

    @Test
    void tellsWhichServicesNeedThePayersAuthorizationOnThatDate() throws Exception {
        String contract = activeEventContract("1.0");
        String body = stepUp(post("/api/v1/contracts/" + contract + "/authorization-requirements"),
                "{\"cupsCode\":\"890201\",\"validFrom\":\"2026-02-01\"}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String requirement = com.jayway.jsonpath.JsonPath.read(body, "$.uuid");

        as("BILLING", post("/api/v1/price-quotes").content(quoteFor(contract, "890201", 1)))
                .andExpect(jsonPath("$.services[0].authorizationRequired").value(true));
        as("BILLING", post("/api/v1/price-quotes").content(quoteFor(contract, "890201", 1).replace("2026-03-01", "2026-01-15")))
                .andExpect(jsonPath("$.services[0].authorizationRequired").value(false));
        as("BILLING", post("/api/v1/price-quotes").content(quoteFor(contract, "999999", 1)))
                .andExpect(jsonPath("$.services[0].authorizationRequired").value(false));

        stepUp(post("/api/v1/contracts/authorization-requirements/" + requirement + "/revocation"),
                "{\"from\":\"2026-03-01\",\"reason\":\"El pagador liberó la consulta\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedFrom").value("2026-03-01"));
        as("BILLING", post("/api/v1/price-quotes").content(quoteFor(contract, "890201", 1)))
                .andExpect(jsonPath("$.services[0].authorizationRequired").value(false));
    }

    @Test
    void reportsServicesWithoutATariff() throws Exception {
        String contract = activeEventContract("1.0");

        as("BILLING", post("/api/v1/price-quotes").content(quoteFor(contract, "999999", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[0].origin").value("UNPRICED"))
                .andExpect(jsonPath("$.services[0].billablePerService").value(false))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void capitationCoversTheServiceInsteadOfPricingIt() throws Exception {
        String contract = activeCapitationContract();

        as("BILLING", post("/api/v1/price-quotes").content(quoteFor(contract, "890201", 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[0].origin").value("CAPITATION"))
                .andExpect(jsonPath("$.services[0].lineTotal").value(0))
                .andExpect(jsonPath("$.tariff").doesNotExist())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void refusesToPriceOutsideTheValidityOfTheContract() throws Exception {
        String contract = activeEventContract("1.0");

        as("BILLING", post("/api/v1/price-quotes").content(
                "{\"contractUuid\":\"" + contract + "\",\"on\":\"2027-03-01\",\"services\":[{\"cupsCode\":\"890201\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_IN_FORCE"));
    }

    @Test
    void keepsPricingAwayFromRolesWithoutThePermission() throws Exception {
        String contract = activeEventContract("1.0");

        as("DOCTOR", post("/api/v1/price-quotes").content(quoteFor(contract, "890201", 1)))
                .andExpect(status().isForbidden());
    }

    private String activeEventContract(String factor) throws Exception {
        return activeContract(manualWithVersion("COP", "1", "40000", "18000"), factor);
    }

    private String activeContract(String tariffVersion, String factor) throws Exception {
        String contract = draftContract("EVENT");
        stepUpWithVersion(put("/api/v1/contracts/" + contract + "/tariff-terms"), 0L,
                "{\"tariffVersionUuid\":\"" + tariffVersion + "\",\"factor\":" + factor + "}")
                .andExpect(status().isOk());
        stepUpWithVersion(post("/api/v1/contracts/" + contract + "/activation"), 1L, "{}")
                .andExpect(status().isOk());
        return contract;
    }

    private String activeCapitationContract() throws Exception {
        String contract = draftContract("CAPITATION");
        stepUpWithVersion(post("/api/v1/contracts/" + contract + "/activation"), 0L, "{}").andExpect(status().isOk());
        stepUp(post("/api/v1/contracts/" + contract + "/capitation-agreement"),
                "{\"perCapitaValue\":38500,\"periodicity\":\"MONTHLY\","
                        + "\"technicalNote\":\"Nota técnica anexa al contrato\",\"validFrom\":\"2026-01-01\"}")
                .andExpect(status().isOk());
        return contract;
    }

    private String draftContract(String modality) throws Exception {
        String nit = String.format("6%08d", SEQUENCE.incrementAndGet());
        String payer = JsonPath.read(as("CONTRACTING", post("/api/v1/payers").content(
                        "{\"identity\":{\"socialReason\":\"Pagador de tarifas\",\"nit\":\"" + nit + "\",\"type\":\"EPS\"},"
                                + "\"contact\":{\"address\":\"Calle 1 # 2-3\",\"phone\":\"6011112222\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.uuid");

        return JsonPath.read(as("CONTRACTING", post("/api/v1/contracts").content(
                        "{\"payerUuid\":\"" + payer + "\",\"number\":\"PRC-" + SEQUENCE.incrementAndGet()
                                + "\",\"name\":\"Contrato de tarifas\",\"modality\":\"" + modality
                                + "\",\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.uuid");
    }

    private String manualWithVersion(String unit, String unitValue, String firstValue, String secondValue) throws Exception {
        String manual = JsonPath.read(as("CONTRACTING", post("/api/v1/tariff-manuals").content(
                        "{\"code\":\"PRC_" + SEQUENCE.incrementAndGet() + "\",\"name\":\"Manual de tarifas\",\"unit\":\""
                                + unit + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.uuid");

        String version = JsonPath.read(as("CONTRACTING", post("/api/v1/tariff-manuals/" + manual + "/versions").content(
                        "{\"label\":\"2026\",\"unitValue\":" + unitValue + ",\"validFrom\":\"2026-01-01\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.uuid");

        as("CONTRACTING", post("/api/v1/tariff-manuals/versions/" + version + "/items").content(
                        "{\"items\":[{\"cupsCode\":\"890201\",\"description\":\"Consulta de medicina general\",\"value\":"
                                + firstValue + "},{\"cupsCode\":\"903841\",\"description\":\"Hemograma IV\",\"value\":"
                                + secondValue + "}]}"))
                .andExpect(status().isOk());
        stepUpWithVersion(post("/api/v1/tariff-manuals/versions/" + version + "/activation"), 1L, "{}")
                .andExpect(status().isOk());
        return version;
    }

    private static String quoteFor(String contract, String cupsCode, int quantity) {
        return "{\"contractUuid\":\"" + contract + "\",\"on\":\"2026-03-01\",\"services\":[{\"cupsCode\":\"" + cupsCode
                + "\",\"quantity\":" + quantity + "}]}";
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

    private ResultActions stepUpWithVersion(MockHttpServletRequestBuilder request, long version, String body) throws Exception {
        UUID contracting = UUID.fromString(JwtTestTokens.USERS.get("CONTRACTING"));
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.IF_MATCH, "\"" + version + "\"")
                .header(HttpHeaders.AUTHORIZATION, SecurityTestTokens.staff("CONTRACTING", contracting).bearer())
                .content(body));
    }
}
