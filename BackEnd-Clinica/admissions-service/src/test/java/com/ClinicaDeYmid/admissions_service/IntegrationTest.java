package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.support.JwtTestTokens;
import com.ClinicaDeYmid.admissions_service.support.SharedPostgres;
import com.ClinicaDeYmid.admissions_service.support.TransitKeys;
import com.ClinicaDeYmid.admissions_service.support.StubbedServices;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TransitKeys.class)
abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void sharedProperties(DynamicPropertyRegistry registry) {
        SharedPostgres.register(registry);
        JwtTestTokens.register(registry);
        registry.add("spring.cloud.openfeign.client.config.patient-service.url", StubbedServices::baseUrl);
        registry.add("spring.cloud.openfeign.client.config.contracting-service.url", StubbedServices::baseUrl);
        registry.add("spring.cloud.openfeign.client.config.practitioners-service.url", StubbedServices::baseUrl);
        registry.add("clinica.admissions.coverage.ttl", () -> "0s");
    }

    @BeforeEach
    void resetStubbedServices() {
        StubbedServices.reset();
    }

    protected String bearer(String role) {
        return JwtTestTokens.bearer(role);
    }

    protected ResultActions as(String role, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role)));
    }

    protected ResultActions as(String role, MockHttpServletRequestBuilder request, String body) throws Exception {
        MockHttpServletRequestBuilder prepared = request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role));
        return mockMvc.perform(body == null ? prepared : prepared.content(body));
    }

    protected ResultActions change(String role, MockHttpServletRequestBuilder request, long version, String body)
            throws Exception {
        MockHttpServletRequestBuilder prepared = request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.IF_MATCH, "\"" + version + "\"")
                .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role));
        return mockMvc.perform(body == null ? prepared.content("{}") : prepared.content(body));
    }
}
