package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.application.AdmissionCommands;
import com.ClinicaDeYmid.admissions_service.application.CatalogueCommands;
import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.Admissions;
import com.ClinicaDeYmid.admissions_service.domain.Cause;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.ServiceType;
import com.ClinicaDeYmid.admissions_service.domain.Triage;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReferences;
import com.ClinicaDeYmid.admissions_service.support.ClinicalEvents;
import com.ClinicaDeYmid.admissions_service.support.JwtTestTokens;
import com.ClinicaDeYmid.admissions_service.support.TransitKeys;
import com.ClinicaDeYmid.admissions_service.support.ProducerContract;
import com.ClinicaDeYmid.admissions_service.support.SharedPostgres;
import com.ClinicaDeYmid.admissions_service.support.TestSequence;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(TransitKeys.class)
class ClinicalEventsConsumerIT {

    private static final String TOPIC = "clinical.encounters.v1";
    private static final String DLT = "clinical.encounters.v1.admissions.dlt";

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

    private static KafkaProducer<String, String> producer;

    @Autowired
    private AdmissionCommands commands;

    @Autowired
    private Admissions admissions;

    @Autowired
    private CatalogueCommands catalogue;

    @Autowired
    private PatientReferences patients;

    @BeforeAll
    static void startKafka() throws Exception {
        KAFKA.start();
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 3, (short) 1))).all().get();
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
        registry.add("clinica.admissions.clinical-events.enabled", () -> true);
    }

    @Test
    void theFixturesFollowTheContractPublishedByClinicalHistory() {
        assertThat(ProducerContract.CLINICAL_EVENTS.breaches(ClinicalEvents.triage(UUID.randomUUID(),
                UUID.randomUUID(), "II", UUID.randomUUID(), Instant.now()))).isEmpty();
        assertThat(ProducerContract.CLINICAL_EVENTS.breaches(ClinicalEvents.progressNote(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), Instant.now()))).isEmpty();
    }

    @Test
    void reflectsTheTriageOfTheEpisodeAndKeepsTheLatestOne() throws Exception {
        Admission episode = anEpisode();
        UUID clinician = UUID.randomUUID();
        Instant first = Instant.now().minusSeconds(600);

        publish(episode.patientUuid(), ClinicalEvents.triage(episode.patientUuid(), episode.uuid(), "III",
                clinician, first));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(triageOf(episode)).isNotNull().satisfies(triage -> {
                    assertThat(triage.level()).isEqualTo(Triage.Level.III);
                    assertThat(triage.byUuid()).isEqualTo(clinician);
                }));

        publish(episode.patientUuid(), ClinicalEvents.triage(episode.patientUuid(), episode.uuid(), "I",
                clinician, first.plusSeconds(300)));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(triageOf(episode).level()).isEqualTo(Triage.Level.I));

        publish(episode.patientUuid(), ClinicalEvents.triage(episode.patientUuid(), episode.uuid(), "V",
                clinician, first.minusSeconds(300)));
        publish(episode.patientUuid(), ClinicalEvents.triage(episode.patientUuid(), episode.uuid(), "II",
                clinician, first.plusSeconds(600)));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(triageOf(episode).level()).isEqualTo(Triage.Level.II));
    }

    @Test
    void ignoresNotesWithoutTriageAndEpisodesItDoesNotKnow() throws Exception {
        Admission episode = anEpisode();

        publish(episode.patientUuid(), ClinicalEvents.progressNote(episode.patientUuid(), episode.uuid(),
                UUID.randomUUID(), Instant.now()));
        publish(episode.patientUuid(), ClinicalEvents.triage(episode.patientUuid(), UUID.randomUUID(), "I",
                UUID.randomUUID(), Instant.now()));

        Thread.sleep(2000);

        assertThat(triageOf(episode)).isNull();
        assertThat(readDeadLetters()).noneMatch(payload -> payload.contains(episode.uuid().toString()));
    }

    @Test
    void sendsAMalformedEventToItsOwnDeadLetterTopic() throws Exception {
        publish(UUID.randomUUID(), "{\"type\":\"ClinicalNoteSigned\",\"data\":{\"triageLevel\":\"II\","
                + "\"admissionUuid\":\"not-a-uuid\"}}");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(readDeadLetters()).isNotEmpty());
    }

    private Triage triageOf(Admission episode) {
        return admissions.findByUuid(episode.uuid()).orElseThrow().triage();
    }

    private Admission anEpisode() {
        UUID patient = UUID.randomUUID();
        patients.saveIfNewer(new PatientReference.Registered(patient, 1,
                new PatientReference.Document("CEDULA_DE_CIUDADANIA", "40" + TestSequence.next()),
                "Ana María", "Restrepo Gómez", LocalDate.of(1990, 4, 12), PatientReference.Sex.FEMALE,
                PatientReference.Registered.Status.ACTIVE, null, "CONTRIBUTORY", null));
        int index = TestSequence.next();
        ServiceType type = catalogue.defineServiceType("Urgencias triage " + index, AdmissionKind.EMERGENCY);
        Location where = catalogue.defineLocation("Sede triage " + index);
        ConfigurationService service = catalogue.configure(type.uuid(), where.uuid());
        return commands.register(patient, service.uuid(), Cause.ILLNESS, null, null);
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
