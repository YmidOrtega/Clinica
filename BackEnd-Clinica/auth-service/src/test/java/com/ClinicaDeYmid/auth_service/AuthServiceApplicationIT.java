package com.ClinicaDeYmid.auth_service;

import com.ClinicaDeYmid.auth_service.domain.password.PasswordPolicy;
import com.ClinicaDeYmid.auth_service.domain.password.PasswordRejectedException;
import com.ClinicaDeYmid.auth_service.domain.throttle.LoginThrottlePolicy;
import com.ClinicaDeYmid.auth_service.support.MySqlTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainer.class)
@TestPropertySource(properties = "eureka.client.enabled=false")
class AuthServiceApplicationIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private PasswordPolicy passwordPolicy;

    @Autowired
    private LoginThrottlePolicy throttlePolicy;

    @Test
    void startsWithTheMigratedSchemaAndTheConfiguredPolicies() {
        assertThat(http.getForObject("/actuator/health/readiness", String.class)).contains("UP");
        assertThat(throttlePolicy).isEqualTo(LoginThrottlePolicy.DEFAULT);
        assertThatThrownBy(() -> passwordPolicy.accept("password123", new PasswordPolicy.Context("ana", List.of())))
                .isInstanceOf(PasswordRejectedException.class);
        assertThatThrownBy(() -> passwordPolicy.accept("clinicadeymid!", new PasswordPolicy.Context("ana", List.of())))
                .isInstanceOf(PasswordRejectedException.class);
    }
}
