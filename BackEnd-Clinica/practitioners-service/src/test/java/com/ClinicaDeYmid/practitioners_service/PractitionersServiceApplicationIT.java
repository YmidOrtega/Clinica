package com.ClinicaDeYmid.practitioners_service;

import com.ClinicaDeYmid.practitioners_service.support.JwtTestTokens;
import com.ClinicaDeYmid.practitioners_service.support.MySqlTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainer.class)
class PractitionersServiceApplicationIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
    }

    @Test
    void startsWithTheMigratedSchema() {
        assertThat(http.getForObject("/actuator/health/readiness", String.class)).contains("UP");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'practitioners_history' AND table_name = 'revisions'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void refusesAnonymousCallers() {
        assertThat(http.getForEntity("/api/v1/practitioners", String.class).getStatusCode().value()).isEqualTo(401);
    }
}
