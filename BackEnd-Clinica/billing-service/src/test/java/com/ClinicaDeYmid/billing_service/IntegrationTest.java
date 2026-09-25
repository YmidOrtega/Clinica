package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.support.JwtTestTokens;
import com.ClinicaDeYmid.billing_service.support.SharedMySql;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void sharedProperties(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry);
        JwtTestTokens.register(registry);
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
}
