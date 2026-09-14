package com.ClinicaDeYmid.clinical_history_service;

import com.ClinicaDeYmid.clinical_history_service.support.Cie10WorkbookFixture;
import com.ClinicaDeYmid.clinical_history_service.support.ClinicalTestProperties;
import com.ClinicaDeYmid.clinical_history_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.clinical_history_service.support.TestJwt;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainer.class)
class TerminologyCatalogIT {

    private static final String BASE = "/api/v1/clinical";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("clinica.clinical.patient-events.enabled", () -> false);
        registry.add("spring.kafka.admin.auto-create", () -> false);
        ClinicalTestProperties.register(registry);
    }

    @Test
    void superAdminsImportAndActivateTheSisproCatalogThatCliniciansSearch() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "Tabla-CIE-10.xlsx", XLSX, Cie10WorkbookFixture.workbook("01-03-2021",
                List.<String[]>of(
                        Cie10WorkbookFixture.row("9", "Enfermedades del sistema circulatorio (I00-I99)", "I10", "Hipertension Esencial (Primaria)",
                                "I10X", "Hipertension esencial (primaria)"),
                        Cie10WorkbookFixture.row("10", "Enfermedades del sistema respiratorio (J00-J99)", "J45", "Asma", "J459",
                                "Asma, no especificado"))));

        as("DOCTOR", get(BASE + "/terminology/cie10").param("q", "asma"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TERMINOLOGY_NOT_ACTIVE"));
        as("DOCTOR", multipart(BASE + "/admin/terminology/cie10/releases").file(file)).andExpect(status().isForbidden());
        String imported = as("SUPER_ADMIN", multipart(BASE + "/admin/terminology/cie10/releases").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.release.version").value("2021-03-01"))
                .andExpect(jsonPath("$.release.conceptCount").value(2))
                .andExpect(jsonPath("$.release.active").value(false))
                .andReturn().getResponse().getContentAsString();
        as("SUPER_ADMIN", multipart(BASE + "/admin/terminology/cie10/releases").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false));
        as("SUPER_ADMIN", multipart(BASE + "/admin/terminology/cie10/releases")
                        .file(new MockMultipartFile("file", "otro.xlsx", XLSX, "texto".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CIE10_FILE"));
        as("SUPER_ADMIN", multipart(BASE + "/admin/terminology/cie10/releases").file(new MockMultipartFile("file", "marzo-v2.xlsx", XLSX,
                        Cie10WorkbookFixture.workbook("01-03-2021", List.<String[]>of(Cie10WorkbookFixture.row("10", "Respiratorio", "J45", "Asma",
                                "J459", "Asma"))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TERMINOLOGY_VERSION_CONFLICT"));
        as("SUPER_ADMIN", post(BASE + "/admin/terminology/cie10/releases/" + JsonPath.read(imported, "$.release.id") + "/activation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        as("DOCTOR", get(BASE + "/terminology/cie10").param("q", "i10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("I10X"));
        as("NURSE", get(BASE + "/terminology/cie10").param("q", "ÁSMA"))
                .andExpect(jsonPath("$[*].code", contains("J459")));
        as("RECEPTIONIST", get(BASE + "/terminology/cie10").param("q", "asma")).andExpect(status().isForbidden());
        as("SUPER_ADMIN", get(BASE + "/admin/terminology/cie10/releases"))
                .andExpect(jsonPath("$[0].checksum").exists());
    }

    private ResultActions as(String role, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(role, UUID.randomUUID())));
    }
}
