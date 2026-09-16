package com.ClinicaDeYmid.patient_service;

import com.ClinicaDeYmid.patient_service.support.JwtTestTokens;
import com.ClinicaDeYmid.patient_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.patient_service.support.PatientJson;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainer.class)
class ContractingServiceOutageIT {

    private static final String PAYER = PatientJson.PAYER_UUID;
    private static final String PAYER_PATH = "/api/v1/payers/" + PAYER;

    @RegisterExtension
    static WireMockExtension contractingService = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
        registry.add("spring.cloud.openfeign.client.config.contracting-service.url", contractingService::baseUrl);
        registry.add("eureka.client.enabled", () -> false);
        registry.add("clinica.patient.payers.fresh-ttl", () -> "0s");
        registry.add("clinica.patient.payers.last-known-ttl", () -> "0s");
        registry.add("resilience4j.circuitbreaker.instances.contracting-service.sliding-window-size", () -> "4");
        registry.add("resilience4j.circuitbreaker.instances.contracting-service.minimum-number-of-calls", () -> "4");
        registry.add("resilience4j.circuitbreaker.instances.contracting-service.wait-duration-in-open-state", () -> "1h");
    }

    @Test
    void keepsServingPatientsAndStopsCallingContractingOnceTheCircuitOpens() throws Exception {
        contractingService.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo(PAYER_PATH))
                .willReturn(okJson(PatientJson.payer(PAYER))));
        String uuid = register();
        circuitBreakers.circuitBreaker("contracting-service").reset();
        contractingService.resetRequests();
        contractingService.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo(PAYER_PATH))
                .willReturn(aResponse().withStatus(500)));

        for (int attempt = 0; attempt < 6; attempt++) {
            mockMvc.perform(get("/api/v1/patients/" + uuid).header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer("DOCTOR")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.payer.availability").value("UNAVAILABLE"));
        }

        assertThat(circuitBreakers.circuitBreaker("contracting-service").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        contractingService.verify(4, getRequestedFor(urlPathEqualTo(PAYER_PATH)));
    }

    private String register() throws Exception {
        String body = mockMvc.perform(post("/api/v1/patients")
                        .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PatientJson.registration(PatientJson.uniqueCedula(), PAYER)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.uuid");
    }
}
