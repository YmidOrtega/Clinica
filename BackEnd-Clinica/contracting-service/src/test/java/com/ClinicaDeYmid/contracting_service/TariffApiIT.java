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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainer.class)
class TariffApiIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
    }

    @Test
    void publishesAManualAndConvertsItsTariffsToPesos() throws Exception {
        String manual = registerManual("SOAT_" + next(), "SMLDV");
        String version = draftVersion(manual, "2026", "47450.00");

        loadTariffs(version)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loaded").value(2))
                .andExpect(jsonPath("$.alreadyLoaded").value(false));

        as("BILLING", get("/api/v1/tariff-manuals/versions/" + version + "/items/890201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(2.5))
                .andExpect(jsonPath("$.valueInPesos").value(118625.00));

        activate(version, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.editable").value(false));
    }

    @Test
    void loadingTheSameFileTwiceChangesNothing() throws Exception {
        String version = draftVersion(registerManual("ISS_" + next(), "COP"), "2001", "1");

        loadTariffs(version).andExpect(status().isOk()).andExpect(jsonPath("$.loaded").value(2));
        loadTariffs(version)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loaded").value(0))
                .andExpect(jsonPath("$.alreadyLoaded").value(true))
                .andExpect(jsonPath("$.itemCount").value(2));
    }

    @Test
    void refusesToChangeTariffsOnceTheVersionIsPublished() throws Exception {
        String version = draftVersion(registerManual("ISS_" + next(), "COP"), "2001", "1");
        loadTariffs(version).andExpect(status().isOk());
        activate(version, 1).andExpect(status().isOk());

        as("CONTRACTING", post("/api/v1/tariff-manuals/versions/" + version + "/items")
                .content(tariffs("9000.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TARIFF_VERSION_NOT_EDITABLE"));
    }

    @Test
    void retiresThePreviousVersionWhenANewOneIsPublished() throws Exception {
        String manual = registerManual("OWN_" + next(), "COP");
        String first = draftVersion(manual, "2025", "1");
        loadTariffs(first).andExpect(status().isOk());
        activate(first, 1).andExpect(status().isOk());

        String second = draftVersion(manual, "2026", "1", "2026-01-01");
        as("CONTRACTING", post("/api/v1/tariff-manuals/versions/" + second + "/items").content(tariffs("9500.00")))
                .andExpect(status().isOk());
        activate(second, 1).andExpect(status().isOk());

        as("BILLING", get("/api/v1/tariff-manuals/" + manual + "/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("2026"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].status").value("RETIRED"));

        as("BILLING", get("/api/v1/tariff-manuals/" + manual + "/versions/in-force?on=2025-06-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("2025"));
    }

    @Test
    void refusesToPublishAVersionWithoutTariffs() throws Exception {
        String version = draftVersion(registerManual("EMPTY_" + next(), "COP"), "2026", "1");

        activate(version, 0)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TARIFF_VERSION_NOT_EDITABLE"));
    }

    @Test
    void demandsAFreshSecondFactorToPublish() throws Exception {
        String version = draftVersion(registerManual("STEPUP_" + next(), "COP"), "2026", "1");
        loadTariffs(version).andExpect(status().isOk());

        UUID contracting = UUID.fromString(JwtTestTokens.USERS.get("CONTRACTING"));
        mockMvc.perform(post("/api/v1/tariff-manuals/versions/" + version + "/activation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .header(HttpHeaders.AUTHORIZATION, SecurityTestTokens.staff("CONTRACTING", contracting)
                                .authenticatedAt(Instant.now().minus(Duration.ofMinutes(30))).bearer()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
    }

    @Test
    void keepsTariffsOutOfReachForRolesWithoutThePermission() throws Exception {
        as("BILLING", post("/api/v1/tariff-manuals").content("{\"code\":\"NOPE_1\",\"name\":\"Manual\",\"unit\":\"COP\"}"))
                .andExpect(status().isForbidden());
    }

    private String registerManual(String code, String unit) throws Exception {
        String body = as("CONTRACTING", post("/api/v1/tariff-manuals")
                .content("{\"code\":\"" + code + "\",\"name\":\"Manual de prueba\",\"unit\":\"" + unit + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.uuid");
    }

    private String draftVersion(String manualUuid, String label, String unitValue) throws Exception {
        return draftVersion(manualUuid, label, unitValue, "2025-01-01");
    }

    private String draftVersion(String manualUuid, String label, String unitValue, String validFrom) throws Exception {
        String body = as("CONTRACTING", post("/api/v1/tariff-manuals/" + manualUuid + "/versions")
                .content("{\"label\":\"" + label + "\",\"unitValue\":" + unitValue + ",\"validFrom\":\"" + validFrom + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.uuid");
    }

    private ResultActions loadTariffs(String versionUuid) throws Exception {
        return as("CONTRACTING", post("/api/v1/tariff-manuals/versions/" + versionUuid + "/items").content(tariffs("2.5")));
    }

    private ResultActions activate(String versionUuid, long version) throws Exception {
        UUID contracting = UUID.fromString(JwtTestTokens.USERS.get("CONTRACTING"));
        return mockMvc.perform(post("/api/v1/tariff-manuals/versions/" + versionUuid + "/activation")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.IF_MATCH, "\"" + version + "\"")
                .header(HttpHeaders.AUTHORIZATION, SecurityTestTokens.staff("CONTRACTING", contracting).bearer()));
    }

    private ResultActions as(String role, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role)));
    }

    private static String tariffs(String firstValue) {
        return "{\"items\":["
                + "{\"cupsCode\":\"890201\",\"description\":\"Consulta de medicina general\",\"value\":" + firstValue + "},"
                + "{\"cupsCode\":\"903841\",\"description\":\"Hemograma IV\",\"value\":1.25}]}";
    }

    private static int next() {
        return SEQUENCE.incrementAndGet();
    }
}
