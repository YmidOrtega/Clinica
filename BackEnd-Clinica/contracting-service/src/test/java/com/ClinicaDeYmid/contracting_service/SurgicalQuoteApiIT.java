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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainer.class)
class SurgicalQuoteApiIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(700);
    private static final String RULES = """
            {"basis":"UVR","components":[
              {"component":"SURGEON","mode":"PER_UNIT","rate":1270,"sameRoutePercent":50,"differentRoutePercent":75},
              {"component":"ANESTHESIOLOGIST","mode":"PER_UNIT","rate":960,"sameRoutePercent":50,"differentRoutePercent":75},
              {"component":"ASSISTANT","mode":"PER_UNIT","rate":360,"minimumBasis":30,"sameRoutePercent":50,
               "differentRoutePercent":75},
              {"component":"OPERATING_ROOM","mode":"BY_RANGE","ranges":[{"from":0,"to":20,"value":30000},
               {"from":20.01,"to":50,"value":60000},{"from":50.01,"to":450,"value":150000}],
               "sameRoutePercent":50,"differentRoutePercent":75},
              {"component":"MATERIALS","mode":"BY_RANGE","ranges":[{"from":0,"to":50,"value":20000},
               {"from":50.01,"to":450,"value":50000}],"sameRoutePercent":50,"differentRoutePercent":75}]}""";

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
    }

    @Test
    void liquidatesAnActWithSeveralProceduresByComponentAndRoute() throws Exception {
        String contract = activeContract(surgicalVersion(true), "1.1");

        as("BILLING", post("/api/v1/price-quotes/surgical").content("""
                {"contractUuid":"%s","on":"2026-03-01","procedures":[
                  {"cupsCode":"512101","route":"piel"},{"cupsCode":"514201","route":"abdominal"},
                  {"cupsCode":"530101","route":"abdominal"}]}""".formatted(contract)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules.basis").value("UVR"))
                .andExpect(jsonPath("$.procedures[1].order").value(1))
                .andExpect(jsonPath("$.procedures[1].principal").value(true))
                .andExpect(jsonPath("$.procedures[1].origin").value("SURGICAL_LIQUIDATION"))
                .andExpect(jsonPath("$.procedures[1].surgicalBasis").value(110))
                .andExpect(jsonPath("$.procedures[1].components[?(@.component == 'SURGEON')].amount").value(153670.00))
                .andExpect(jsonPath("$.procedures[1].total").value(533390.00))
                .andExpect(jsonPath("$.procedures[2].sameRoute").value(true))
                .andExpect(jsonPath("$.procedures[2].total").value(195470.00))
                .andExpect(jsonPath("$.procedures[0].route").value("PIEL"))
                .andExpect(jsonPath("$.procedures[0].sameRoute").value(false))
                .andExpect(jsonPath("$.procedures[0].components.length()").value(4))
                .andExpect(jsonPath("$.procedures[0].total").value(78045.00))
                .andExpect(jsonPath("$.componentTotals.SURGEON").value(153670.00 + 41910.00 + 20955.00))
                .andExpect(jsonPath("$.total").value(806905.00));
    }

    @Test
    void aSimpleServiceInTheSameActKeepsItsOwnTariff() throws Exception {
        String contract = activeContract(surgicalVersion(true), "1.0");

        as("BILLING", post("/api/v1/price-quotes/surgical").content("""
                {"contractUuid":"%s","on":"2026-03-01","procedures":[{"cupsCode":"514201"},{"cupsCode":"890201"}]}"""
                .formatted(contract)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.procedures[1].origin").value("TARIFF_MANUAL"))
                .andExpect(jsonPath("$.procedures[1].total").value(40000.00))
                .andExpect(jsonPath("$.procedures[1].components.length()").value(0))
                .andExpect(jsonPath("$.total").value(484900.00 + 40000.00));
    }

    @Test
    void theRegularQuoteSendsSurgeriesToTheSurgicalLiquidation() throws Exception {
        String contract = activeContract(surgicalVersion(true), "1.0");

        as("BILLING", post("/api/v1/price-quotes").content(
                "{\"contractUuid\":\"" + contract + "\",\"on\":\"2026-03-01\",\"services\":[{\"cupsCode\":\"514201\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[0].origin").value("UNPRICED"))
                .andExpect(jsonPath("$.services[0].surgical").value(true));
    }

    @Test
    void aVersionWithSurgeriesIsNotPublishedWithoutItsRules() throws Exception {
        String version = surgicalVersion(false);

        stepUpWithVersion(post("/api/v1/tariff-manuals/versions/" + version + "/activation"), 1L, "{}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SURGICAL_RULES_MISSING"));

        as("CONTRACTING", post("/api/v1/tariff-manuals/versions/" + version + "/surgical-rules").content(RULES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.length()").value(5));
        as("CONTRACTING", post("/api/v1/tariff-manuals/versions/" + version + "/surgical-rules").content(RULES))
                .andExpect(status().isOk());
        as("CONTRACTING", post("/api/v1/tariff-manuals/versions/" + version + "/surgical-rules")
                .content(RULES.replace("1270", "1300")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SURGICAL_RULES_ALREADY_LOADED"));

        String published = stepUpWithVersion(post("/api/v1/tariff-manuals/versions/" + version + "/activation"), 2L, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surgicalItemCount").value(3))
                .andReturn().getResponse().getContentAsString();
        as("CONTRACTING", post("/api/v1/tariff-manuals/versions/" + version + "/surgical-rules").content(RULES))
                .andExpect(status().isOk());
        as("BILLING", get("/api/v1/tariff-manuals/versions/" + version + "/surgical-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checksum").value((String) JsonPath.read(published, "$.surgicalRulesChecksum")));
    }

    @Test
    void refusesRulesThatDoNotAddUp() throws Exception {
        String version = draftVersionWithItems();

        as("CONTRACTING", post("/api/v1/tariff-manuals/versions/" + version + "/surgical-rules").content("""
                {"basis":"UVR","components":[{"component":"ANESTHESIOLOGIST","mode":"PER_UNIT","rate":960,
                  "sameRoutePercent":50,"differentRoutePercent":75}]}"""))
                .andExpect(status().isBadRequest());
        as("CONTRACTING", post("/api/v1/tariff-manuals/versions/" + version + "/surgical-rules").content("""
                {"basis":"UVR","components":[{"component":"SURGEON","mode":"BY_RANGE","ranges":[
                  {"from":0,"to":50,"value":1},{"from":40,"to":90,"value":2}],
                  "sameRoutePercent":50,"differentRoutePercent":75}]}"""))
                .andExpect(status().isBadRequest());
        as("BILLING", post("/api/v1/tariff-manuals/versions/" + version + "/surgical-rules").content(RULES))
                .andExpect(status().isForbidden());
    }

    private String surgicalVersion(boolean withRules) throws Exception {
        String version = draftVersionWithItems();
        if (!withRules) {
            return version;
        }
        as("CONTRACTING", post("/api/v1/tariff-manuals/versions/" + version + "/surgical-rules").content(RULES))
                .andExpect(status().isOk());
        stepUpWithVersion(post("/api/v1/tariff-manuals/versions/" + version + "/activation"), 2L, "{}")
                .andExpect(status().isOk());
        return version;
    }

    private String draftVersionWithItems() throws Exception {
        String manual = JsonPath.read(as("CONTRACTING", post("/api/v1/tariff-manuals").content(
                        "{\"code\":\"QX_" + SEQUENCE.incrementAndGet() + "\",\"name\":\"Manual quirúrgico\",\"unit\":\"COP\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.uuid");
        String version = JsonPath.read(as("CONTRACTING", post("/api/v1/tariff-manuals/" + manual + "/versions").content(
                        "{\"label\":\"2026\",\"unitValue\":1,\"validFrom\":\"2026-01-01\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.uuid");
        as("CONTRACTING", post("/api/v1/tariff-manuals/versions/" + version + "/items").content("""
                {"items":[
                  {"cupsCode":"890201","description":"Consulta de medicina general","value":40000},
                  {"cupsCode":"514201","description":"Colecistectomía","value":0,"surgicalBasis":110},
                  {"cupsCode":"530101","description":"Herniorrafia inguinal","value":0,"surgicalBasis":60},
                  {"cupsCode":"512101","description":"Resección de lesión de piel","value":0,"surgicalBasis":20}]}"""))
                .andExpect(status().isOk());
        return version;
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

    private String draftContract(String modality) throws Exception {
        String nit = String.format("5%08d", SEQUENCE.incrementAndGet());
        String payer = JsonPath.read(as("CONTRACTING", post("/api/v1/payers").content(
                        "{\"identity\":{\"socialReason\":\"Pagador de tarifas\",\"nit\":\"" + nit + "\",\"type\":\"EPS\"},"
                                + "\"contact\":{\"address\":\"Calle 1 # 2-3\",\"phone\":\"6011112222\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.uuid");

        return JsonPath.read(as("CONTRACTING", post("/api/v1/contracts").content(
                        "{\"payerUuid\":\"" + payer + "\",\"number\":\"QXC-" + SEQUENCE.incrementAndGet()
                                + "\",\"name\":\"Contrato de tarifas\",\"modality\":\"" + modality
                                + "\",\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.uuid");
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
