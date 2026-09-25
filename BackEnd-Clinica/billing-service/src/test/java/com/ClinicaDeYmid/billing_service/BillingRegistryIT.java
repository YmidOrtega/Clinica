package com.ClinicaDeYmid.billing_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillingRegistryIT extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theMigrationCreatesTheSchemasTheServiceWillUse() {
        assertThat(jdbc.queryForList("SELECT schema_name FROM information_schema.schemata", String.class))
                .contains("billing_history", "billing_outbox");
    }

    @Test
    void theRevisionTableRefusesAnAuthorThatIsNotAUuid() {
        assertThatThrownBy(() -> revise("not-a-uuid")).hasMessageContaining("chk_revisions_revised_by");
        assertThat(revise("3f2504e0-4f89-11d3-9a0c-0305e82c3301")).isEqualTo(1);
        assertThat(revise(null)).isEqualTo(1);
    }

    @Test
    void reportsReadyWithoutASession() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void refusesAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/v1/billing/accounts"))
                .andExpect(status().isUnauthorized());
    }

    private int revise(String author) {
        return jdbc.update("INSERT INTO billing_history.revisions (revised_at, revised_by) VALUES (NOW(6), ?)", author);
    }
}
