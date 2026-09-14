package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.support.MySqlTestContainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class InnoDbEncryptionIT {

    private static final String MARKER = "RESTREPO-MARCADOR-EN-DISCO";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MySqlTestContainer.IMAGE).withUsername("root");

    private static JdbcTemplate root;

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration/clinical").load().migrate();
        root = new JdbcTemplate(new SingleConnectionDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword(), true));
    }

    @Test
    void theServerEncryptsTablespacesRedoUndoAndBinlog() {
        assertThat(root.queryForObject("""
                SELECT STATUS_VALUE FROM performance_schema.keyring_component_status WHERE STATUS_KEY = 'Component_status'""",
                String.class)).isEqualTo("Active");
        assertThat(root.queryForList("""
                SELECT VARIABLE_NAME, VARIABLE_VALUE FROM performance_schema.global_variables
                WHERE VARIABLE_NAME IN ('innodb_redo_log_encrypt', 'innodb_undo_log_encrypt', 'binlog_encryption', 'default_table_encryption')""")
                .stream().map(row -> row.get("VARIABLE_VALUE"))).containsOnly("ON");
    }

    @Test
    void everyClinicalTableIsEncryptedAtRest() {
        assertThat(root.queryForList("""
                SELECT t.TABLE_SCHEMA, t.TABLE_NAME, s.ENCRYPTION
                FROM information_schema.TABLES t
                JOIN information_schema.INNODB_TABLESPACES s ON s.NAME = CONCAT(t.TABLE_SCHEMA, '/', t.TABLE_NAME)
                WHERE t.TABLE_SCHEMA IN (DATABASE(), 'clinical_ledger', 'clinical_workspace', 'clinical_keys')""")
                .stream().map(row -> row.get("ENCRYPTION"))).hasSizeGreaterThanOrEqualTo(8).containsOnly("Y");
    }

    @Test
    void dataFilesDoNotRevealPlaintextWhileAnUnencryptedTableWould() throws Exception {
        root.update("""
                INSERT INTO patient_references (uuid, kind, source_version, document_type, document_number, first_names, last_names,
                    birth_date, sex, status, health_regime, updated_at)
                VALUES (UUID(), 'REGISTERED', 0, 'CEDULA_DE_CIUDADANIA', '1098765432', 'Ana', ?, '1990-04-12', 'FEMALE', 'ACTIVE',
                    'CONTRIBUTORY', NOW(6))""", MARKER);
        root.execute("CREATE TABLE plain_control (v VARCHAR(64)) ENCRYPTION = 'N'");
        root.update("INSERT INTO plain_control VALUES (?)", MARKER);
        root.execute("FLUSH TABLES patient_references, plain_control FOR EXPORT");
        root.execute("UNLOCK TABLES");

        String database = MYSQL.getDatabaseName();

        assertThat(grep("/var/lib/mysql/" + database + "/plain_control.ibd").getExitCode()).isZero();
        assertThat(grep("/var/lib/mysql/" + database + "/patient_references.ibd").getExitCode()).isEqualTo(1);
    }

    private static ExecResult grep(String file) throws Exception {
        return MYSQL.execInContainer("grep", "-c", MARKER, file);
    }
}
