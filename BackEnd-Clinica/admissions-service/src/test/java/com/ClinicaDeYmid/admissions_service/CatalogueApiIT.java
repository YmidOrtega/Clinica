package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.support.JwtTestTokens;
import com.ClinicaDeYmid.admissions_service.support.PostgresTestContainer;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class CatalogueApiIT {

    private static final String BASE = "/api/v1/admissions/catalogue";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
    }

    @Test
    void definesAServiceTypeAndSaysWhetherItNeedsABed() throws Exception {
        String name = unique("Hospitalización");
        as("ADMIN", post(BASE + "/service-types").content(serviceType(name, "INPATIENT")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.kind").value("INPATIENT"))
                .andExpect(jsonPath("$.bedRequired").value(true))
                .andExpect(jsonPath("$.status.code").value("ACTIVE"));

        as("ADMIN", post(BASE + "/service-types").content(serviceType(unique("Urgencias"), "EMERGENCY")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bedRequired").value(false));
    }

    @Test
    void refusesTwoServiceTypesWithTheSameName() throws Exception {
        String name = unique("Consulta externa");
        as("ADMIN", post(BASE + "/service-types").content(serviceType(name, "OUTPATIENT")))
                .andExpect(status().isCreated());

        as("ADMIN", post(BASE + "/service-types").content(serviceType(name, "OUTPATIENT")))
                .andExpect(status().isConflict());
    }

    @Test
    void retiresAndRestoresAServiceTypeWithoutDeletingIt() throws Exception {
        String uuid = uuidOf(as("ADMIN", post(BASE + "/service-types")
                .content(serviceType(unique("UCI"), "INPATIENT"))).andReturn().getResponse().getContentAsString());

        change("ADMIN", post(BASE + "/service-types/" + uuid + "/retirement"), 0,
                "{\"reason\":\"Se unificó con hospitalización\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("RETIRED"))
                .andExpect(jsonPath("$.status.reason").value("Se unificó con hospitalización"));

        change("ADMIN", post(BASE + "/service-types/" + uuid + "/restoration"), 1, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("ACTIVE"))
                .andExpect(jsonPath("$.status.reason").doesNotExist());

        as("NURSE", get(BASE + "/service-types/" + uuid)).andExpect(status().isOk());
    }

    @Test
    void demandsTheCurrentVersionToChangeAnything() throws Exception {
        String uuid = uuidOf(as("ADMIN", post(BASE + "/locations")
                .content("{\"name\":\"" + unique("Piso") + "\"}")).andReturn().getResponse().getContentAsString());

        as("ADMIN", put(BASE + "/locations/" + uuid).content("{\"name\":\"Otro\"}"))
                .andExpect(status().isPreconditionRequired());

        change("ADMIN", put(BASE + "/locations/" + uuid), 7, "{\"name\":\"Otro\"}")
                .andExpect(status().isPreconditionFailed());

        change("ADMIN", put(BASE + "/locations/" + uuid), 0, "{\"name\":\"" + unique("Piso renombrado") + "\"}")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""));
    }

    @Test
    void configuresAServiceInALocationOnlyOnce() throws Exception {
        String serviceType = uuidOf(as("ADMIN", post(BASE + "/service-types")
                .content(serviceType(unique("Cirugía"), "INPATIENT"))).andReturn().getResponse().getContentAsString());
        String location = uuidOf(as("ADMIN", post(BASE + "/locations")
                .content("{\"name\":\"" + unique("Torre") + "\"}")).andReturn().getResponse().getContentAsString());

        String body = "{\"serviceTypeUuid\":\"" + serviceType + "\",\"locationUuid\":\"" + location + "\"}";
        as("ADMIN", post(BASE + "/configured-services").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("INPATIENT"));

        as("ADMIN", post(BASE + "/configured-services").content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void refusesToConfigureARetiredLocation() throws Exception {
        String serviceType = uuidOf(as("ADMIN", post(BASE + "/service-types")
                .content(serviceType(unique("Observación"), "EMERGENCY"))).andReturn().getResponse().getContentAsString());
        String location = uuidOf(as("ADMIN", post(BASE + "/locations")
                .content("{\"name\":\"" + unique("Ala") + "\"}")).andReturn().getResponse().getContentAsString());
        change("ADMIN", post(BASE + "/locations/" + location + "/retirement"), 0, "{\"reason\":\"Remodelación\"}")
                .andExpect(status().isOk());

        as("ADMIN", post(BASE + "/configured-services")
                .content("{\"serviceTypeUuid\":\"" + serviceType + "\",\"locationUuid\":\"" + location + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void groupsCareTypesUnderTheirServiceType() throws Exception {
        String serviceType = uuidOf(as("ADMIN", post(BASE + "/service-types")
                .content(serviceType(unique("Urgencias"), "EMERGENCY"))).andReturn().getResponse().getContentAsString());

        as("ADMIN", post(BASE + "/care-types")
                .content("{\"name\":\"Consulta prioritaria\",\"serviceTypeUuid\":\"" + serviceType + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceTypeUuid").value(serviceType));

        as("ADMIN", post(BASE + "/care-types")
                .content("{\"name\":\"Consulta prioritaria\",\"serviceTypeUuid\":\"" + serviceType + "\"}"))
                .andExpect(status().isConflict());

        as("DOCTOR", get(BASE + "/service-types/" + serviceType + "/care-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Consulta prioritaria"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOCTOR", "NURSE", "RECEPTIONIST", "MEDICAL_RECORDS", "BILLING"})
    void everyStaffRoleReadsTheCatalogueButOnlyAdministrationChangesIt(String role) throws Exception {
        as(role, get(BASE + "/service-types")).andExpect(status().isOk());
        as(role, post(BASE + "/locations").content("{\"name\":\"" + unique("Intento") + "\"}"))
                .andExpect(status().isForbidden());
    }

    private ResultActions as(String role, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role)));
    }

    private ResultActions change(String role, MockHttpServletRequestBuilder request, long version, String body)
            throws Exception {
        MockHttpServletRequestBuilder prepared = request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.IF_MATCH, "\"" + version + "\"")
                .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role));
        return mockMvc.perform(body == null ? prepared.content("{}") : prepared.content(body));
    }

    private static String serviceType(String name, String kind) {
        return "{\"name\":\"" + name + "\",\"kind\":\"" + kind + "\"}";
    }

    private static String uuidOf(String json) {
        return JsonPath.read(json, "$.uuid");
    }

    private static String unique(String prefix) {
        return prefix + " " + SEQUENCE.incrementAndGet();
    }
}
