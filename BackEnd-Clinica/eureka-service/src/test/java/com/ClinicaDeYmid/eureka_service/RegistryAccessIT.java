package com.ClinicaDeYmid.eureka_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"EUREKA_USERNAME=registry", "EUREKA_PASSWORD=registry-test-secret"})
class RegistryAccessIT {

    private static final String INSTANCE = """
            {"instance":{"instanceId":"billing-service:1","hostName":"billing-service","app":"BILLING-SERVICE",
             "ipAddr":"10.0.0.9","status":"UP","port":{"$":8082,"@enabled":"true"},
             "dataCenterInfo":{"@class":"com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo","name":"MyOwn"}}}""";

    @Autowired
    private TestRestTemplate http;

    @Test
    void nobodyReadsOrWritesTheRegistryWithoutCredentials() {
        assertThat(http.getForEntity("/eureka/apps", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.exchange("/eureka/apps/BILLING-SERVICE", HttpMethod.POST, json(INSTANCE), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.withBasicAuth("registry", "wrong").getForEntity("/eureka/apps", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.getForEntity("/", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aServiceWithTheCredentialsRegistersAndIsFound() {
        TestRestTemplate client = http.withBasicAuth("registry", "registry-test-secret");

        assertThat(client.exchange("/eureka/apps/BILLING-SERVICE", HttpMethod.POST, json(INSTANCE), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        HttpHeaders accept = new HttpHeaders();
        accept.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        assertThat(client.exchange("/eureka/apps/BILLING-SERVICE", HttpMethod.GET, new HttpEntity<>(accept), String.class)
                .getBody()).contains("billing-service");
    }

    @Test
    void theHealthProbeStaysOpenForDocker() {
        assertThat(http.getForEntity("/actuator/health/readiness", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private static HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
