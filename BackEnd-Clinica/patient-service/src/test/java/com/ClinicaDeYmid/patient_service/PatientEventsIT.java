package com.ClinicaDeYmid.patient_service;

import com.ClinicaDeYmid.patient_service.support.JwtTestTokens;
import com.ClinicaDeYmid.patient_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.patient_service.support.PatientEventContract;
import com.ClinicaDeYmid.patient_service.support.PatientJson;
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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;

import static com.ClinicaDeYmid.patient_service.support.SecretFiles.withKafkaConnectSecrets;
import static com.ClinicaDeYmid.patient_service.support.SecretFiles.withSecretFiles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PatientEventsIT {

    private static final String TOPIC = "patient.events.v1";
    private static final String CONNECTOR = "patient-outbox";
    private static final String DEBEZIUM_USER = "patient_debezium";
    private static final String DEBEZIUM_PASSWORD = "debezium-test-secret";
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(60);

    private static final Network NETWORK = Network.newNetwork();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final List<ConsumerRecord<String, String>> RECEIVED = new ArrayList<>();

    private static final MySQLContainer<?> MYSQL = withSecretFiles(new MySQLContainer<>(MySqlTestContainer.IMAGE)
            .withUsername("root")
            .withNetwork(NETWORK)
            .withNetworkAliases("patient-db")
            .withCopyFileToContainer(MountableFile.forHostPath("docker/mysql-init/01-create-users.sh", 0755),
                    "/docker-entrypoint-initdb.d/01-create-users.sh"), Map.of(
            "PATIENT_DB_MIGRATOR_USER", "patient_migrator",
            "PATIENT_DB_MIGRATOR_PASSWORD", "migrator-test-secret",
            "PATIENT_DB_APP_USER", "patient_app",
            "PATIENT_DB_APP_PASSWORD", "app-test-secret",
            "PATIENT_DB_DEBEZIUM_USER", DEBEZIUM_USER,
            "PATIENT_DB_DEBEZIUM_PASSWORD", DEBEZIUM_PASSWORD));

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1")
            .withNetwork(NETWORK)
            .withListener("kafka:19092")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

    private static GenericContainer<?> connect;
    private static KafkaConsumer<String, String> consumer;

    @Autowired
    private MockMvc mockMvc;

    @BeforeAll
    static void startEventPipeline() throws Exception {
        MYSQL.start();
        KAFKA.start();
        connect = startConnect();
        registerConnector();
        consumer = new KafkaConsumer<>(consumerProperties());
        consumer.subscribe(List.of(TOPIC));
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
        JwtTestTokens.register(registry);
        registry.add("eureka.client.enabled", () -> false);
    }

    @Test
    @Order(1)
    void publishesTheRegistrationWithTheSharedSnapshotOnly() throws Exception {
        String document = PatientJson.uniqueCedula();
        String uuid = register(document);

        ConsumerRecord<String, String> record = awaitEvents(uuid, events -> !events.isEmpty()).getFirst();
        JsonNode event = JSON.readTree(record.value());

        assertThat(record.key()).isEqualTo(uuid);
        assertThat(new String(record.headers().lastHeader("eventType").value(), StandardCharsets.UTF_8))
                .contains("PatientRegistered");
        assertThat(event.path("type").asText()).isEqualTo("PatientRegistered");
        assertThat(event.path("patientVersion").asLong()).isZero();
        assertThat(event.at("/data/patient/document/number").asText()).isEqualTo(document);
        assertThat(PatientEventContract.violations(record.value())).isEmpty();
        assertThat(record.value()).doesNotContain("mobile", "residence", "address");
    }

    @Test
    @Order(2)
    void publishesStatusChangesInOrderAndSkipsContactUpdates() throws Exception {
        String uuid = register(PatientJson.uniqueCedula());
        modify(uuid, 0, put("/api/v1/patients/" + uuid + "/contact").content("{\"contact\": {\"mobile\": \"3150001122\"}}"));
        modify(uuid, 1, post("/api/v1/patients/" + uuid + "/deactivation").content("{\"reason\": \"Registro duplicado\"}"));
        modify(uuid, 2, post("/api/v1/patients/" + uuid + "/reactivation"));

        List<ConsumerRecord<String, String>> events = awaitEvents(uuid, received -> received.size() >= 3);

        assertThat(events).extracting(record -> field(record, "type"))
                .containsExactly("PatientRegistered", "PatientDeactivated", "PatientReactivated");
        assertThat(events).extracting(record -> field(record, "patientVersion"))
                .containsExactly("0", "2", "3");
        assertThat(events).extracting(ConsumerRecord::partition).containsOnly(events.getFirst().partition());
        assertThat(events).allSatisfy(record -> assertThat(PatientEventContract.violations(record.value())).isEmpty());
    }

    @Test
    @Order(3)
    void keepsEventsWhileKafkaConnectIsDownAndDeliversThemAfterwards() throws Exception {
        connect.stop();

        String uuid = register(PatientJson.uniqueCedula());

        connect = startConnect();
        awaitConnectorRunning();
        assertThat(awaitEvents(uuid, events -> !events.isEmpty()))
                .extracting(record -> field(record, "type"))
                .containsExactly("PatientRegistered");
    }

    @Test
    @Order(4)
    void storesEventsInACompactedTopicSoNewConsumersCanRebuildTheRegistry() throws Exception {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            ConfigResource topic = new ConfigResource(ConfigResource.Type.TOPIC, TOPIC);
            Config config = admin.describeConfigs(List.of(topic)).all().get().get(topic);

            assertThat(config.get("cleanup.policy").value()).isEqualTo("compact");
            assertThat(admin.describeTopics(List.of(TOPIC)).allTopicNames().get().get(TOPIC).partitions()).hasSize(3);
        }
    }

    @Test
    @Order(5)
    void publishesUnidentifiedPatientsAndTheirIdentificationWithoutTheDescription() throws Exception {
        String arrival = mockMvc.perform(authorized(post("/api/v1/unidentified-patients")
                        .content("{\"sex\": \"FEMALE\", \"estimatedBirthYear\": 1992, \"description\": \"Mujer con vestido verde\"}")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String unidentifiedUuid = JsonPath.read(arrival, "$.uuid");
        String patientUuid = register(PatientJson.uniqueCedula());
        mockMvc.perform(authorized(post("/api/v1/unidentified-patients/" + unidentifiedUuid + "/identification")
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .content("{\"patientUuid\": \"" + patientUuid + "\", \"reason\": \"Cédula\"}")))
                .andExpect(status().isOk());

        List<ConsumerRecord<String, String>> events = awaitEvents(unidentifiedUuid, received -> received.size() >= 2);

        assertThat(events).extracting(record -> field(record, "type"))
                .containsExactly("UnidentifiedPatientRegistered", "UnidentifiedPatientIdentified");
        assertThat(JSON.readTree(events.get(1).value()).at("/data/unidentifiedPatient/identifiedPatientUuid").asText())
                .isEqualTo(patientUuid);
        assertThat(events).allSatisfy(record -> {
            assertThat(PatientEventContract.violations(record.value())).isEmpty();
            assertThat(record.value()).doesNotContain("vestido", "Cédula");
        });
    }

    private String register(String document) throws Exception {
        String body = mockMvc.perform(authorized(post("/api/v1/patients").content(PatientJson.uninsuredRegistration(document))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.uuid");
    }

    private void modify(String uuid, long version, MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(authorized(request.header(HttpHeaders.IF_MATCH, "\"" + version + "\"")))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer("ADMIN")).contentType(MediaType.APPLICATION_JSON);
    }

    private static List<ConsumerRecord<String, String>> awaitEvents(String patientUuid,
                                                                    Predicate<List<ConsumerRecord<String, String>>> done) {
        Instant deadline = Instant.now().plus(EVENT_TIMEOUT);
        while (true) {
            consumer.poll(Duration.ofMillis(500)).forEach(RECEIVED::add);
            List<ConsumerRecord<String, String>> matching = RECEIVED.stream()
                    .filter(record -> patientUuid.equals(record.key()))
                    .toList();
            if (done.test(matching)) {
                return matching;
            }
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Events for patient " + patientUuid + " not received in time: " + matching);
            }
        }
    }

    private static String field(ConsumerRecord<String, String> record, String name) {
        try {
            return JSON.readTree(record.value()).path(name).asText();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static GenericContainer<?> startConnect() {
        GenericContainer<?> container = withKafkaConnectSecrets(new GenericContainer<>("quay.io/debezium/connect:3.6.2.Final")
                .withNetwork(NETWORK)
                .withExposedPorts(8083)
                .withEnv("BOOTSTRAP_SERVERS", "kafka:19092")
                .withEnv("GROUP_ID", "patient-events-it")
                .withEnv("CONFIG_STORAGE_TOPIC", "connect.configs")
                .withEnv("OFFSET_STORAGE_TOPIC", "connect.offsets")
                .withEnv("STATUS_STORAGE_TOPIC", "connect.status")
                .withEnv("CONFIG_STORAGE_REPLICATION_FACTOR", "1")
                .withEnv("OFFSET_STORAGE_REPLICATION_FACTOR", "1")
                .withEnv("STATUS_STORAGE_REPLICATION_FACTOR", "1")
                .withEnv("PATIENT_DB_HOST", "patient-db")
                .withEnv("KAFKA_REPLICATION_FACTOR", "1")
                .waitingFor(Wait.forHttp("/connectors").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2))), Map.of(
                "patient-db-debezium-user", DEBEZIUM_USER,
                "patient-db-debezium-password", DEBEZIUM_PASSWORD));
        container.start();
        return container;
    }

    private static void registerConnector() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(connectUri("/connectors/" + CONNECTOR + "/config"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(Files.readString(Path.of("debezium/" + CONNECTOR + ".json"))))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isIn(200, 201);
        awaitConnectorRunning();
    }

    private static void awaitConnectorRunning() throws Exception {
        Instant deadline = Instant.now().plus(EVENT_TIMEOUT);
        String status = "";
        while (Instant.now().isBefore(deadline)) {
            HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(connectUri("/connectors/" + CONNECTOR + "/status")).build(),
                    HttpResponse.BodyHandlers.ofString());
            status = response.body();
            if (response.statusCode() == 200 && status.matches("(?s).*\"connector\":\\{\"state\":\"RUNNING\".*\"tasks\":\\[\\{\"id\":0,\"state\":\"RUNNING\".*")) {
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
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "patient-events-it-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, "1000");
        properties.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return properties;
    }
}
