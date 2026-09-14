package com.ClinicaDeYmid.clinical_history_service;

import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReferences;
import com.ClinicaDeYmid.clinical_history_service.support.AccessAuditContract;
import com.ClinicaDeYmid.clinical_history_service.support.ClinicalTestProperties;
import com.ClinicaDeYmid.clinical_history_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.clinical_history_service.support.TestJwt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static com.ClinicaDeYmid.clinical_history_service.support.SecretFiles.withKafkaConnectSecrets;
import static com.ClinicaDeYmid.clinical_history_service.support.SecretFiles.withSecretFiles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClinicalEventsIT {

    private static final String TOPIC = "clinical.access-audit.v1";
    private static final String ENCOUNTERS_TOPIC = "clinical.encounters.v1";
    private static final String CONNECTOR = "clinical-outbox";
    private static final String DEBEZIUM_USER = "clinical_debezium";
    private static final String DEBEZIUM_PASSWORD = "debezium-test-secret";
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(90);

    private static final Network NETWORK = Network.newNetwork();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final List<ConsumerRecord<String, String>> RECEIVED = new ArrayList<>();

    private static final MySQLContainer<?> MYSQL = withSecretFiles(new MySQLContainer<>(MySqlTestContainer.IMAGE)
            .withUsername("root")
            .withNetwork(NETWORK)
            .withNetworkAliases("clinical-db")
            .withCopyFileToContainer(MountableFile.forHostPath("docker/mysql-init/01-create-users.sh", 0755),
                    "/docker-entrypoint-initdb.d/01-create-users.sh"), Map.of(
            "CLINICAL_DB_MIGRATOR_USER", "clinical_migrator",
            "CLINICAL_DB_MIGRATOR_PASSWORD", "migrator-test-secret",
            "CLINICAL_DB_APP_USER", "clinical_app",
            "CLINICAL_DB_APP_PASSWORD", "app-test-secret",
            "CLINICAL_DB_DEBEZIUM_USER", DEBEZIUM_USER,
            "CLINICAL_DB_DEBEZIUM_PASSWORD", DEBEZIUM_PASSWORD));

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1")
            .withNetwork(NETWORK)
            .withListener("kafka:19092")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

    private static GenericContainer<?> connect;
    private static KafkaConsumer<String, String> consumer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PatientReferences patients;

    @BeforeAll
    static void startEventPipeline() throws Exception {
        MYSQL.start();
        KAFKA.start();
        connect = withKafkaConnectSecrets(new GenericContainer<>("quay.io/debezium/connect:3.6.2.Final")
                .withNetwork(NETWORK)
                .withExposedPorts(8083)
                .withEnv("BOOTSTRAP_SERVERS", "kafka:19092")
                .withEnv("GROUP_ID", "clinical-events-it")
                .withEnv("CONFIG_STORAGE_TOPIC", "connect.configs")
                .withEnv("OFFSET_STORAGE_TOPIC", "connect.offsets")
                .withEnv("STATUS_STORAGE_TOPIC", "connect.status")
                .withEnv("CONFIG_STORAGE_REPLICATION_FACTOR", "1")
                .withEnv("OFFSET_STORAGE_REPLICATION_FACTOR", "1")
                .withEnv("STATUS_STORAGE_REPLICATION_FACTOR", "1")
                .withEnv("CLINICAL_DB_HOST", "clinical-db")
                .withEnv("KAFKA_REPLICATION_FACTOR", "1")
                .waitingFor(Wait.forHttp("/connectors").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2))), Map.of(
                "clinical-db-debezium-user", DEBEZIUM_USER,
                "clinical-db-debezium-password", DEBEZIUM_PASSWORD));
        connect.start();
        consumer = new KafkaConsumer<>(consumerProperties());
    }

    @AfterAll
    static void stopEventPipeline() {
        consumer.close();
        connect.stop();
        KAFKA.stop();
        MYSQL.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
        registry.add("clinica.clinical.patient-events.enabled", () -> false);
        registry.add("spring.kafka.admin.auto-create", () -> false);
        ClinicalTestProperties.register(registry);
    }

    @Test
    void publishesAccessDecisionsToAnAppendOnlyTopicKeyedByPatient() throws Exception {
        registerConnector();
        UUID patient = UUID.randomUUID();
        patients.saveIfNewer(new PatientReference.Registered(patient, 0, new PatientReference.Document("CEDULA_DE_CIUDADANIA", "1098765432"),
                "Ana", "Restrepo", LocalDate.of(1990, 4, 12), PatientReference.Sex.FEMALE, PatientReference.Registered.Status.ACTIVE, null,
                "CONTRIBUTORY", null));
        UUID nurse = UUID.randomUUID();
        String opened = mockMvc.perform(as("NURSE", nurse, post("/api/v1/clinical/encounters")
                        .content("{\"patientUuid\": \"" + patient + "\", \"type\": \"EMERGENCY\"}")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String encounter = JsonPath.read(opened, "$.id");
        mockMvc.perform(as("DOCTOR", UUID.randomUUID(), get("/api/v1/clinical/encounters/" + encounter)))
                .andExpect(status().isForbidden());

        List<ConsumerRecord<String, String>> events = awaitEvents(patient.toString(), 2).stream()
                .filter(record -> TOPIC.equals(record.topic())).toList();

        assertThat(events).extracting(record -> new String(record.headers().lastHeader("eventType").value(), StandardCharsets.UTF_8))
                .allSatisfy(type -> assertThat(type).containsAnyOf("ClinicalRecordAccessed", "ClinicalRecordAccessDenied"));
        assertThat(events).extracting(record -> field(record.value(), "action")).containsExactly("OPEN_ENCOUNTER", "READ_ENCOUNTER");
        assertThat(events).extracting(record -> field(record.value(), "outcome")).containsExactly("GRANTED", "DENIED");
        assertThat(events).extracting(ConsumerRecord::partition).containsOnly(events.getFirst().partition());
        assertThat(events).allSatisfy(record -> assertThat(AccessAuditContract.violations(record.value())).isEmpty());

        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            ConfigResource topic = new ConfigResource(ConfigResource.Type.TOPIC, TOPIC);
            Config config = admin.describeConfigs(List.of(topic)).all().get().get(topic);

            assertThat(config.get("cleanup.policy").value()).isEqualTo("delete");
            assertThat(config.get("retention.ms").value()).isEqualTo("-1");
            assertThat(admin.describeTopics(List.of(TOPIC)).allTopicNames().get().get(TOPIC).partitions()).hasSize(3);
        }

        List<ConsumerRecord<String, String>> facts = awaitEvents(patient.toString(), 3).stream()
                .filter(record -> ENCOUNTERS_TOPIC.equals(record.topic())).toList();
        assertThat(facts).hasSize(1);
        assertThat(field(facts.getFirst().value(), "type")).isEqualTo("EncounterOpened");
        assertThat(new String(facts.getFirst().headers().lastHeader("eventType").value(), StandardCharsets.UTF_8)).contains("EncounterOpened");
        assertThat(AccessAuditContract.encounterEventViolations(facts.getFirst().value())).isEmpty();
    }

    private static MockHttpServletRequestBuilder as(String role, UUID user, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(role, user)).contentType(MediaType.APPLICATION_JSON);
    }

    private static List<ConsumerRecord<String, String>> awaitEvents(String patientUuid, int expected) throws Exception {
        Instant deadline = Instant.now().plus(EVENT_TIMEOUT);
        while (true) {
            if (consumer.subscription().size() < 2 && topicExists(TOPIC) && topicExists(ENCOUNTERS_TOPIC)) {
                consumer.subscribe(List.of(TOPIC, ENCOUNTERS_TOPIC));
            }
            if (!consumer.subscription().isEmpty()) {
                consumer.poll(Duration.ofMillis(500)).forEach(RECEIVED::add);
            } else {
                Thread.sleep(500);
            }
            List<ConsumerRecord<String, String>> matching = RECEIVED.stream().filter(record -> patientUuid.equals(record.key())).toList();
            if (matching.size() >= expected) {
                return matching;
            }
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Audit events for patient " + patientUuid + " not received in time: " + matching);
            }
        }
    }

    private static boolean topicExists(String topic) throws Exception {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            return admin.listTopics().names().get().contains(topic);
        }
    }

    private static String field(String json, String name) {
        try {
            JsonNode node = JSON.readTree(json);
            return node.path(name).asText();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void registerConnector() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(connectUri("/connectors/" + CONNECTOR + "/config"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(Files.readString(Path.of("debezium/" + CONNECTOR + ".json"))))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isIn(200, 201);
        Instant deadline = Instant.now().plus(EVENT_TIMEOUT);
        String status = "";
        while (Instant.now().isBefore(deadline)) {
            HttpResponse<String> current = HTTP.send(HttpRequest.newBuilder(connectUri("/connectors/" + CONNECTOR + "/status")).build(),
                    HttpResponse.BodyHandlers.ofString());
            status = current.body();
            if (current.statusCode() == 200 && status.matches("(?s).*\"connector\":\\{\"state\":\"RUNNING\".*\"tasks\":\\[\\{\"id\":0,\"state\":\"RUNNING\".*")) {
                return;
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Connector did not reach RUNNING: " + status);
    }

    private static URI connectUri(String path) {
        return URI.create("http://" + connect.getHost() + ":" + connect.getMappedPort(8083) + path);
    }

    private static Properties consumerProperties() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "clinical-events-it-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return properties;
    }
}
