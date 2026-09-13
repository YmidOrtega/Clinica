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
class ClientsServiceOutageIT {

    private static final String NIT = "860000001-5";
    private static final String PROVIDER_PATH = "/api/v1/billing-service/health-providers/" + NIT;

    @RegisterExtension
    static WireMockExtension clientsService = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("clinica.security.jwt.public-key", JwtTestTokens::publicKeyBase64);
        registry.add("spring.cloud.openfeign.client.config.clients-service.url", clientsService::baseUrl);
        registry.add("eureka.client.enabled", () -> false);
        registry.add("clinica.patient.health-providers.fresh-ttl", () -> "0s");
        registry.add("clinica.patient.health-providers.last-known-ttl", () -> "0s");
        registry.add("resilience4j.circuitbreaker.instances.clients-service.sliding-window-size", () -> "4");
        registry.add("resilience4j.circuitbreaker.instances.clients-service.minimum-number-of-calls", () -> "4");
        registry.add("resilience4j.circuitbreaker.instances.clients-service.wait-duration-in-open-state", () -> "1h");
    }

    @Test
    void keepsServingPatientsAndStopsCallingClientsServiceOnceTheCircuitOpens() throws Exception {
        clientsService.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo(PROVIDER_PATH))
                .willReturn(okJson(PatientJson.healthProvider(NIT))));
        String uuid = register();
        circuitBreakers.circuitBreaker("clients-service").reset();
        clientsService.resetRequests();
        clientsService.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo(PROVIDER_PATH))
                .willReturn(aResponse().withStatus(500)));

        for (int attempt = 0; attempt < 6; attempt++) {
            mockMvc.perform(get("/api/v1/patients/" + uuid).header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer("DOCTOR")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.healthProvider.availability").value("UNAVAILABLE"));
        }

        assertThat(circuitBreakers.circuitBreaker("clients-service").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        clientsService.verify(4, getRequestedFor(urlPathEqualTo(PROVIDER_PATH)));
    }

    private String register() throws Exception {
        String body = mockMvc.perform(post("/api/v1/patients")
                        .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PatientJson.registration(PatientJson.uniqueCedula(), NIT)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.uuid");
    }
}
