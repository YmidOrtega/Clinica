package com.ClinicaDeYmid.clinical_history_service;

import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReferences;
import com.ClinicaDeYmid.clinical_history_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.clinical_history_service.support.PatientEvents;
import com.ClinicaDeYmid.clinical_history_service.support.TestJwt;
import com.ClinicaDeYmid.clinical_history_service.support.TestSealKeys;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(MySqlTestContainer.class)
class PatientEventsConsumerIT {

    private static final String TOPIC = "patient.events.v1";
    private static final String DLT = "patient.events.v1.clinical-history.dlt";

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

    private static KafkaProducer<String, String> producer;

    @Autowired
    private PatientReferences references;

    @BeforeAll
    static void startKafka() throws Exception {
        KAFKA.start();
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 3, (short) 1)
                    .configs(Map.of("cleanup.policy", "compact")))).all().get();
        }
        producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()));
    }

    @AfterAll
    static void stopKafka() {
        producer.close();
        KAFKA.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("clinica.security.jwt.public-key", TestJwt::publicKeyBase64);
        registry.add("clinica.clinical.seal.keys-location", TestSealKeys::directory);
        registry.add("clinica.clinical.seal.active-key-id", () -> TestSealKeys.ACTIVE_KEY_ID);
        registry.add("eureka.client.enabled", () -> false);
    }

    @Test
    void keepsALocalCopyOfRegisteredPatients() throws Exception {
        UUID uuid = UUID.randomUUID();

        publish(uuid, PatientEvents.registered(uuid, 0));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(references.find(uuid)).isPresent());
    }

    @Test
    void ignoresEventsThatArriveOutOfOrder() throws Exception {
        UUID uuid = UUID.randomUUID();
        UUID marker = UUID.randomUUID();

        publish(uuid, PatientEvents.registered(uuid, 2, "PatientDeactivated", "INACTIVE", null));
        publish(uuid, PatientEvents.registered(uuid, 1, "PatientRegistered", "ACTIVE", null));
        publish(marker, PatientEvents.registered(marker, 0));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(references.find(marker)).isPresent());
        assertThat(references.find(uuid)).hasValueSatisfying(reference -> {
            assertThat(reference.version()).isEqualTo(2);
            assertThat(((PatientReference.Registered) reference).status()).isEqualTo(PatientReference.Registered.Status.INACTIVE);
        });
    }

    @Test
    void followsTheIdentificationOfUnidentifiedPatients() throws Exception {
        UUID patient = UUID.randomUUID();
        UUID provisional = UUID.randomUUID();
        publish(patient, PatientEvents.registered(patient, 0));
        publish(provisional, PatientEvents.unidentified(provisional, 0, "UnidentifiedPatientRegistered", "UNIDENTIFIED", null, null));
        publish(provisional, PatientEvents.unidentified(provisional, 1, "UnidentifiedPatientIdentified", "IDENTIFIED", patient, null));

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(references.subjectsOf(patient)).containsExactly(patient, provisional));

        publish(provisional, PatientEvents.unidentified(provisional, 2, "UnidentifiedPatientIdentificationReverted", "UNIDENTIFIED", null, patient));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(references.subjectsOf(patient)).containsExactly(patient));
    }

    @Test
    void sendsBrokenEventsToTheDeadLetterTopicAndKeepsConsuming() throws Exception {
        UUID broken = UUID.randomUUID();
        UUID next = UUID.randomUUID();

        publish(broken, "{\"type\": \"PatientRegistered\", \"patientVersion\": 0, \"data\": {\"patient\": {}}}");
        publish(next, PatientEvents.registered(next, 0));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(references.find(next)).isPresent());
        assertThat(deadLetters()).extracting(ConsumerRecord::key).contains(broken.toString());
    }

    private static void publish(UUID key, String value) throws Exception {
        producer.send(new ProducerRecord<>(TOPIC, key.toString(), value)).get();
    }

    private static List<ConsumerRecord<String, String>> deadLetters() {
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-reader-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()))) {
            consumer.subscribe(List.of(DLT));
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
                return !records.isEmpty();
            });
        }
        return records;
    }
}
