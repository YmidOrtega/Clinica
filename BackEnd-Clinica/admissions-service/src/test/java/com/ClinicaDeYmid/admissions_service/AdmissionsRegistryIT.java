package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.support.PostgresTestContainer;
import com.ClinicaDeYmid.admissions_service.support.JwtTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestContainer.class)
class AdmissionsRegistryIT {

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
    }

    @Test
    void theMigrationCreatesTheSchemasTheServiceWillUse() {
        assertThat(schemas()).contains("admissions", "admissions_history", "admissions_outbox");
    }

    @Test
    void theExclusionExtensionIsAvailableForBedOccupancy() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'", Integer.class)).isEqualTo(1);
    }

    @Test
    void theRevisionTableRefusesAnAuthorThatIsNotAUuid() {
        assertThatInsertingRevisedBy("not-a-uuid").isFalse();
        assertThatInsertingRevisedBy("3f2504e0-4f89-11d3-9a0c-0305e82c3301").isTrue();
        assertThatInsertingRevisedBy(null).isTrue();
    }

    private org.assertj.core.api.AbstractBooleanAssert<?> assertThatInsertingRevisedBy(String author) {
        boolean accepted;
        try {
            jdbc.update("INSERT INTO admissions_history.revisions (revised_at, revised_by) VALUES (now(), ?)", author);
            accepted = true;
        } catch (org.springframework.dao.DataIntegrityViolationException rejected) {
            accepted = false;
        }
        return assertThat(accepted);
    }

    private java.util.List<String> schemas() {
        return jdbc.queryForList("SELECT schema_name FROM information_schema.schemata", String.class);
    }
}
