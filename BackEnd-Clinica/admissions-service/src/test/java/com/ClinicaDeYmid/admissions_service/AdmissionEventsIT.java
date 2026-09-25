package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.application.CatalogueCommands;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.ServiceType;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReferences;
import com.ClinicaDeYmid.admissions_service.support.JwtTestTokens;
import com.ClinicaDeYmid.admissions_service.support.TransitKeys;
import com.ClinicaDeYmid.admissions_service.support.ProducerContract;
import com.ClinicaDeYmid.admissions_service.support.SharedPostgres;
import com.ClinicaDeYmid.admissions_service.support.StubbedServices;
import com.ClinicaDeYmid.admissions_service.support.TestSequence;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import java.util.function.Predicate;

import static com.ClinicaDeYmid.admissions_service.support.SecretFiles.withKafkaConnectSecrets;
import static com.ClinicaDeYmid.admissions_service.support.SecretFiles.withSecretFiles;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TransitKeys.class)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdmissionEventsIT {

    private static final String TOPIC = "admissions.events.v1";
    private static final String CONNECTOR = "admissions-outbox";
    private static final String EPISODES = "/api/v1/admissions/episodes";
    private static final String DEBEZIUM_USER = "admissions_debezium";
    private static final String DEBEZIUM_PASSWORD = "debezium-test-secret";
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(60);

    private static final Network NETWORK = Network.newNetwork();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final List<ConsumerRecord<String, String>> RECEIVED = new ArrayList<>();

    private static final PostgreSQLContainer<?> POSTGRES = withSecretFiles(
            new PostgreSQLContainer<>(SharedPostgres.IMAGE)
                    .withNetwork(NETWORK)
                    .withNetworkAliases("admissions-db")
                    .withCommand("postgres", "-c", "wal_level=logical", "-c", "max_replication_slots=4",
                            "-c", "max_wal_senders=4")
                    .withCopyFileToContainer(MountableFile.forHostPath("docker/postgres-init/01-create-users.sh", 0755),
                            "/docker-entrypoint-initdb.d/01-create-users.sh"), Map.of(
                    "ADMISSIONS_DB_MIGRATOR_USER", "admissions_migrator",
                    "ADMISSIONS_DB_MIGRATOR_PASSWORD", "migrator-test-secret",
                    "ADMISSIONS_DB_APP_USER", "admissions_app",
                    "ADMISSIONS_DB_APP_PASSWORD", "app-test-secret",
                    "ADMISSIONS_DB_DEBEZIUM_USER", DEBEZIUM_USER,
                    "ADMISSIONS_DB_DEBEZIUM_PASSWORD", DEBEZIUM_PASSWORD));

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1")
            .withNetwork(NETWORK)
            .withListener("kafka:19092")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

    private static GenericContainer<?> connect;
    private static KafkaConsumer<String, String> consumer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CatalogueCommands catalogue;

    @Autowired
    private PatientReferences patients;

    @BeforeAll
    static void startEventPipeline() throws Exception {
        POSTGRES.start();
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
        POSTGRES.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.cloud.openfeign.client.config.patient-service.url", StubbedServices::baseUrl);
        registry.add("spring.cloud.openfeign.client.config.contracting-service.url", StubbedServices::baseUrl);
        registry.add("spring.cloud.openfeign.client.config.practitioners-service.url", StubbedServices::baseUrl);
        registry.add("clinica.admissions.coverage.ttl", () -> "0s");
        JwtTestTokens.register(registry);
    }

    @Test
    @Order(1)
    void publishesTheRegistrationAndThePendingCoverageWithTheSummary() throws Exception {
        StubbedServices.reset();
        StubbedServices.server().stubFor(get(urlPathEqualTo("/api/v1/contracts")).willReturn(okJson("[]")));
        String episode = register(emergency());

        List<ConsumerRecord<String, String>> events = awaitEvents(episode, received -> received.size() >= 2);

        assertThat(events).extracting(record -> field(record, "type"))
                .containsExactly("AdmissionRegistered", "AdmissionCoveragePending");
        assertThat(events).allSatisfy(record -> {
            assertThat(ProducerContract.ADMISSION_EVENTS.breaches(record.value())).isEmpty();
            assertThat(JSON.readTree(record.value()).at("/data/admission/kind").asText()).isEqualTo("EMERGENCY");
        });
        assertThat(JSON.readTree(events.get(1).value()).at("/data/admission/coverage").asText())
                .isEqualTo("NOT_COVERED");
    }

    @Test
    @Order(2)
    void publishesTheBedAndTheDischargeInOrder() throws Exception {
        String episode = register(emergency());
        String bed = aBed();
        change(episode, 0, "/bed", "{\"bedUuid\":\"" + bed + "\"}");
        change(episode, 1, "/activation", null);
        change(episode, 2, "/discharge", "{\"type\":\"MEDICAL\"}");

        List<ConsumerRecord<String, String>> events = awaitEvents(episode, received -> received.size() >= 5);

        assertThat(events).extracting(record -> field(record, "type"))
                .containsExactly("AdmissionRegistered", "AdmissionCoveragePending", "AdmissionBedAssigned",
                        "AdmissionBedReleased", "AdmissionDischarged");
        assertThat(JSON.readTree(events.get(2).value()).at("/data/bedUuid").asText()).isEqualTo(bed);
        assertThat(JSON.readTree(events.get(2).value()).at("/data/admission/bedStayType").asText()).isEqualTo("ICU_ADULT");
        assertThat(JSON.readTree(events.get(3).value()).at("/data/admission/bedStayType").isMissingNode()).isTrue();
        assertThat(JSON.readTree(events.get(4).value()).at("/data/discharge").asText()).isEqualTo("MEDICAL");
        assertThat(events).allSatisfy(record ->
                assertThat(ProducerContract.ADMISSION_EVENTS.breaches(record.value())).isEmpty());
    }

    @Test
    @Order(3)
    void keepsEventsWhileKafkaConnectIsDownAndDeliversThemAfterwards() throws Exception {
        connect.stop();
        String episode = register(emergency());
        change(episode, 0, "/cancellation", "{\"reason\":\"Se registró dos veces\"}");

        connect = startConnect();
        registerConnector();

        List<ConsumerRecord<String, String>> events = awaitEvents(episode, received -> received.size() >= 3);

        assertThat(events).extracting(record -> field(record, "type"))
                .containsExactly("AdmissionRegistered", "AdmissionCoveragePending", "AdmissionCancelled");
        assertThat(JSON.readTree(events.get(2).value()).at("/data/admission/status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    @Order(4)
    void keysEveryRecordByTheEpisodeSoTheLatestOneIsItsCurrentState() throws Exception {
        String episode = register(emergency());
        change(episode, 0, "/activation", null);

        List<ConsumerRecord<String, String>> events = awaitEvents(episode, received -> received.size() >= 1);

        assertThat(events).allSatisfy(record -> {
            assertThat(record.key()).isEqualTo(episode);
            assertThat(record.headers().lastHeader("eventType")).isNotNull();
        });
        assertThat(RECEIVED).extracting(ConsumerRecord::topic).containsOnly(TOPIC);
    }

    private String register(UUID service) throws Exception {
        UUID patient = UUID.randomUUID();
        patients.saveIfNewer(new PatientReference.Registered(patient, 1,
                new PatientReference.Document("CEDULA_DE_CIUDADANIA", "80" + TestSequence.next()),
                "Ana María", "Restrepo Gómez", LocalDate.of(1990, 4, 12), PatientReference.Sex.FEMALE,
                PatientReference.Registered.Status.ACTIVE, null, "CONTRIBUTORY", UUID.randomUUID().toString()));
        String body = mockMvc.perform(authorized(post(EPISODES), "RECEPTIONIST")
                        .content("{\"patientUuid\":\"" + patient + "\",\"configurationServiceUuid\":\"" + service
                                + "\",\"cause\":\"ILLNESS\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.uuid");
    }

    private void change(String episode, long version, String action, String body) throws Exception {
        MockHttpServletRequestBuilder request = authorized(post(EPISODES + "/" + episode + action), "ADMIN")
                .header(HttpHeaders.IF_MATCH, "\"" + version + "\"");
        mockMvc.perform(body == null ? request.content("{}") : request.content(body)).andExpect(status().isOk());
    }

    private String aBed() throws Exception {
        int index = TestSequence.next();
        Location location = catalogue.defineLocation("Piso eventos " + index);
        String room = JsonPath.read(mockMvc.perform(authorized(post("/api/v1/admissions/rooms"), "ADMIN")
                        .content("{\"name\":\"Hab eventos " + index + "\",\"locationUuid\":\"" + location.uuid() + "\",\"stayType\":\"ICU_ADULT\"}"))
                .andReturn().getResponse().getContentAsString(), "$.uuid");
        return JsonPath.read(mockMvc.perform(authorized(post("/api/v1/admissions/beds"), "ADMIN")
                        .content("{\"label\":\"Cama eventos " + index + "\",\"roomUuid\":\"" + room + "\"}"))
                .andReturn().getResponse().getContentAsString(), "$.uuid");
    }

    private UUID emergency() {
        int index = TestSequence.next();
        ServiceType type = catalogue.defineServiceType("Urgencias eventos " + index, AdmissionKind.EMERGENCY);
        Location where = catalogue.defineLocation("Sede eventos " + index);
        ConfigurationService configured = catalogue.configure(type.uuid(), where.uuid());
        return configured.uuid();
    }

    private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String role) {
        return request.header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private static List<ConsumerRecord<String, String>> awaitEvents(String episode,
                                                                    Predicate<List<ConsumerRecord<String, String>>> done) {
        Instant deadline = Instant.now().plus(EVENT_TIMEOUT);
        while (true) {
            consumer.poll(Duration.ofMillis(500)).forEach(RECEIVED::add);
            List<ConsumerRecord<String, String>> matching = RECEIVED.stream()
                    .filter(record -> episode.equals(record.key()))
                    .toList();
            if (done.test(matching)) {
                return matching;
            }
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Events for admission " + episode + " not received in time: " + matching
                        + System.lineSeparator() + "connector status: " + connectorStatus()
                        + System.lineSeparator() + "slots: " + slots()
                        + System.lineSeparator() + "connect log: " + tail(connect.getLogs()));
            }
        }
    }

    private static String tail(String logs) {
        return logs.length() <= 6000 ? logs : logs.substring(logs.length() - 6000);
    }

    private static String slots() {
        try (java.sql.Connection connection = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
             java.sql.Statement statement = connection.createStatement();
             java.sql.ResultSet rows = statement.executeQuery(
                     "SELECT slot_name, active, restart_lsn, confirmed_flush_lsn, pg_current_wal_lsn() "
                             + "FROM pg_replication_slots")) {
            StringBuilder slots = new StringBuilder();
            while (rows.next()) {
                slots.append(rows.getString(1)).append(" active=").append(rows.getBoolean(2))
                        .append(" restart=").append(rows.getString(3))
                        .append(" confirmed=").append(rows.getString(4))
                        .append(" current=").append(rows.getString(5)).append("; ");
            }
            return slots.isEmpty() ? "no slots" : slots.toString();
        } catch (Exception ex) {
            return "unavailable: " + ex;
        }
    }

    private static String connectorStatus() {
        try {
            return HTTP.send(HttpRequest.newBuilder(connectUri("/connectors/" + CONNECTOR + "/status")).build(),
                    HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception ex) {
            return "unavailable: " + ex;
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
        GenericContainer<?> container = withKafkaConnectSecrets(
                new GenericContainer<>("quay.io/debezium/connect:3.6.2.Final")
                        .withNetwork(NETWORK)
                        .withExposedPorts(8083)
                        .withEnv("BOOTSTRAP_SERVERS", "kafka:19092")
                        .withEnv("GROUP_ID", "admissions-events-it")
                        .withEnv("CONFIG_STORAGE_TOPIC", "connect.configs")
                        .withEnv("OFFSET_STORAGE_TOPIC", "connect.offsets")
                        .withEnv("STATUS_STORAGE_TOPIC", "connect.status")
                        .withEnv("CONFIG_STORAGE_REPLICATION_FACTOR", "1")
                        .withEnv("OFFSET_STORAGE_REPLICATION_FACTOR", "1")
                        .withEnv("STATUS_STORAGE_REPLICATION_FACTOR", "1")
                        .withEnv("ADMISSIONS_DB_HOST", "admissions-db")
                        .withEnv("ADMISSIONS_DB_NAME", POSTGRES.getDatabaseName())
                        .withEnv("KAFKA_REPLICATION_FACTOR", "1")
                        .waitingFor(Wait.forHttp("/connectors").forStatusCode(200)
                                .withStartupTimeout(Duration.ofMinutes(2))), Map.of(
                        "admissions-db-debezium-user", DEBEZIUM_USER,
                        "admissions-db-debezium-password", DEBEZIUM_PASSWORD));
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
        HTTP.send(HttpRequest.newBuilder(connectUri("/connectors/" + CONNECTOR
                        + "/restart?includeTasks=true&onlyFailed=true"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        awaitConnectorRunning();
    }

    private static void awaitConnectorRunning() throws Exception {
        Instant deadline = Instant.now().plus(EVENT_TIMEOUT);
        String status = "";
        while (Instant.now().isBefore(deadline)) {
            HttpResponse<String> response = HTTP.send(
                    HttpRequest.newBuilder(connectUri("/connectors/" + CONNECTOR + "/status")).build(),
                    HttpResponse.BodyHandlers.ofString());
            status = response.body();
            if (response.statusCode() == 200 && status.matches(
                    "(?s).*\"connector\":\\{\"state\":\"RUNNING\".*\"tasks\":\\[\\{\"id\":0,\"state\":\"RUNNING\".*")) {
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
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "admissions-events-it-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, "1000");
        properties.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return properties;
    }
}
