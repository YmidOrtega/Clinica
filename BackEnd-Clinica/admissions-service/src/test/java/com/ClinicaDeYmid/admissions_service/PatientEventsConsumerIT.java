package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReferences;
import com.ClinicaDeYmid.admissions_service.support.JwtTestTokens;
import com.ClinicaDeYmid.admissions_service.support.PatientEvents;
import com.ClinicaDeYmid.admissions_service.support.SharedPostgres;
import com.ClinicaDeYmid.admissions_service.support.ProducerContract;
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
class PatientEventsConsumerIT {

    private static final String TOPIC = "patient.events.v1";
    private static final String DLT = "patient.events.v1.admissions.dlt";

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
        SharedPostgres.register(registry);
        JwtTestTokens.register(registry);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("clinica.admissions.patient-events.enabled", () -> true);
    }

    @Test
    void theFixturesFollowTheContractPublishedByPatientService() {
        assertThat(ProducerContract.PATIENT_EVENTS.breaches(PatientEvents.registered(UUID.randomUUID(), 1))).isEmpty();
        assertThat(ProducerContract.PATIENT_EVENTS.breaches(PatientEvents.unidentified(
                UUID.randomUUID(), 1, "UnidentifiedPatientRegistered", "UNIDENTIFIED", null, null))).isEmpty();
    }

    @Test
    void keepsALocalCopyOfRegisteredPatients() throws Exception {
        UUID uuid = UUID.randomUUID();

        publish(uuid, PatientEvents.registered(uuid, 1));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(references.find(uuid)).get()
                        .isInstanceOf(PatientReference.Registered.class)
                        .satisfies(reference -> {
                            assertThat(reference.version()).isEqualTo(1);
                            assertThat(reference.admissible()).isTrue();
                            assertThat(reference.label()).isEqualTo("Ana María Restrepo Gómez");
                        }));
    }

    @Test
    void keepsALocalCopyOfUnidentifiedPatients() throws Exception {
        UUID uuid = UUID.randomUUID();

        publish(uuid, PatientEvents.unidentified(uuid, 1, "UnidentifiedPatientRegistered", "UNIDENTIFIED", null, null));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(references.find(uuid)).get()
                        .isInstanceOf(PatientReference.Unidentified.class)
                        .satisfies(reference -> assertThat(reference.label()).isEqualTo("NN-2026-000042")));
    }

    @Test
    void ignoresAnEventOlderThanTheCopyItAlreadyHas() throws Exception {
        UUID uuid = UUID.randomUUID();

        publish(uuid, PatientEvents.registered(uuid, 5, "PatientDeactivated", "INACTIVE", null));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(references.find(uuid)).get().extracting(PatientReference::version).isEqualTo(5L));

        publish(uuid, PatientEvents.registered(uuid, 2, "PatientRegistered", "ACTIVE", null));
        publish(uuid, PatientEvents.registered(uuid, 6, "PatientRegistered", "ACTIVE", null));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(references.find(uuid)).get().extracting(PatientReference::version).isEqualTo(6L));
        assertThat(references.find(uuid)).get().extracting(PatientReference::admissible).isEqualTo(true);
    }

    @Test
    void aDeceasedPatientIsNoLongerAdmissible() throws Exception {
        UUID uuid = UUID.randomUUID();

        publish(uuid, PatientEvents.registered(uuid, 3, "PatientDied", "DECEASED", "2026-09-19"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(references.find(uuid)).get().extracting(PatientReference::admissible).isEqualTo(false));
    }

    @Test
    void sendsAMalformedEventToItsOwnDeadLetterTopic() throws Exception {
        UUID uuid = UUID.randomUUID();

        publish(uuid, "{\"type\":\"PatientRegistered\",\"patientVersion\":1,\"data\":{\"patient\":{}}}");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(readDeadLetters()).isNotEmpty());
    }

    private void publish(UUID key, String payload) throws Exception {
        producer.send(new ProducerRecord<>(TOPIC, key.toString(), payload)).get();
    }

    private List<String> readDeadLetters() {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-reader-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()))) {
            consumer.subscribe(List.of(DLT));
            List<String> payloads = new ArrayList<>();
            consumer.poll(Duration.ofSeconds(5)).forEach((ConsumerRecord<String, String> record) ->
                    payloads.add(record.value()));
            return payloads;
        }
    }
}
