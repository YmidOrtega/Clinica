package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.application.AccountQueries;
import com.ClinicaDeYmid.billing_service.domain.AccountStatus;
import com.ClinicaDeYmid.billing_service.domain.AdmissionKind;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.DischargeType;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;
import com.ClinicaDeYmid.billing_service.support.AdmissionEvents;
import com.ClinicaDeYmid.billing_service.support.JwtTestTokens;
import com.ClinicaDeYmid.billing_service.support.ProducerContract;
import com.ClinicaDeYmid.billing_service.support.SharedMySql;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class AdmissionEventsConsumerIT {

    private static final String TOPIC = "admissions.events.v1";
    private static final String DLT = "admissions.events.v1.billing.dlt";

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

    private static KafkaProducer<String, String> producer;

    @Autowired
    private AccountQueries accounts;

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
        SharedMySql.register(registry);
        JwtTestTokens.register(registry);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("clinica.billing.admission-events.enabled", () -> true);
        registry.add("spring.kafka.admin.auto-create", () -> true);
    }

    @Test
    void theFixturesFollowTheContractPublishedByAdmissions() {
        UUID admission = UUID.randomUUID();
        String number = AdmissionEvents.nextNumber();

        assertThat(ProducerContract.ADMISSION_EVENTS.breaches(AdmissionEvents.registered(admission, number, 0))).isEmpty();
        assertThat(ProducerContract.ADMISSION_EVENTS.breaches(
                AdmissionEvents.phaseChanged(admission, number, 1, "EMERGENCY"))).isEmpty();
        assertThat(ProducerContract.ADMISSION_EVENTS.breaches(
                AdmissionEvents.discharged(admission, number, 2, "MEDICAL"))).isEmpty();
        assertThat(ProducerContract.ADMISSION_EVENTS.breaches(
                AdmissionEvents.cancelled(admission, number, 2, "Registrado por error"))).isEmpty();
    }

    @Test
    void opensTheAccountWhenTheEpisodeIsRegisteredAndFreezesItAtDischarge() throws Exception {
        UUID admission = UUID.randomUUID();
        String number = AdmissionEvents.nextNumber();

        publish(admission, AdmissionEvents.registered(admission, number, 0));
        await().atMost(Duration.ofSeconds(20)).ignoreExceptions().untilAsserted(() ->
                assertThat(accounts.byAdmissionNumber(number).status()).isInstanceOf(AccountStatus.Open.class));

        publish(admission, AdmissionEvents.phaseChanged(admission, number, 1, "EMERGENCY"));
        publish(admission, AdmissionEvents.discharged(admission, number, 2, "DEATH"));

        await().atMost(Duration.ofSeconds(20)).ignoreExceptions().untilAsserted(() -> {
            EpisodeAccount account = accounts.byAdmissionNumber(number);
            assertThat(account.status()).isInstanceOf(AccountStatus.Frozen.class);
            assertThat(((AccountStatus.Frozen) account.status()).discharge()).isEqualTo(DischargeType.DEATH);
            assertThat(account.kind()).isEqualTo(AdmissionKind.INPATIENT);
            assertThat(account.admissionVersion()).isEqualTo(2);
        });
    }

    @Test
    void voidsTheAccountWhenTheAdmissionIsCancelled() throws Exception {
        UUID admission = UUID.randomUUID();
        String number = AdmissionEvents.nextNumber();

        publish(admission, AdmissionEvents.registered(admission, number, 0));
        publish(admission, AdmissionEvents.cancelled(admission, number, 1, "Registrado por error"));

        await().atMost(Duration.ofSeconds(20)).ignoreExceptions().untilAsserted(() ->
                assertThat(accounts.byAdmissionNumber(number).status())
                        .isEqualTo(new AccountStatus.Voided("Registrado por error",
                                Instant.parse("2026-09-25T15:00:00Z"))));
    }

    @Test
    void anEpisodeFirstSeenAfterCompactionStillGetsItsAccount() throws Exception {
        UUID admission = UUID.randomUUID();
        String number = AdmissionEvents.nextNumber();

        publish(admission, AdmissionEvents.discharged(admission, number, 7, "MEDICAL"));

        await().atMost(Duration.ofSeconds(20)).ignoreExceptions().untilAsserted(() ->
                assertThat(accounts.byAdmissionNumber(number).status()).isInstanceOf(AccountStatus.Frozen.class));
    }

    @Test
    void anOlderEventArrivingLateChangesNothing() throws Exception {
        UUID admission = UUID.randomUUID();
        String number = AdmissionEvents.nextNumber();

        publish(admission, AdmissionEvents.phaseChanged(admission, number, 4, "EMERGENCY"));
        publish(admission, AdmissionEvents.registered(admission, number, 1));
        publish(admission, AdmissionEvents.phaseChanged(admission, number, 5, "OUTPATIENT"));

        await().atMost(Duration.ofSeconds(20)).ignoreExceptions().untilAsserted(() ->
                assertThat(accounts.byAdmissionNumber(number).admissionVersion()).isEqualTo(5));
        assertThat(accounts.byAdmissionNumber(number).kind()).isEqualTo(AdmissionKind.OUTPATIENT);
    }

    @Test
    void sendsAMalformedEventToItsOwnDeadLetterTopic() throws Exception {
        UUID admission = UUID.randomUUID();
        String number = AdmissionEvents.nextNumber();

        publish(admission, "{\"type\":\"AdmissionRegistered\",\"admissionUuid\":\"" + admission
                + "\",\"admissionVersion\":0,\"occurredAt\":\"2026-09-25T15:00:00Z\",\"data\":{\"admission\":{\"number\":\""
                + number + "\"}}}");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(readDeadLetters()).anySatisfy(
                payload -> assertThat(payload).contains(number)));
        assertThatThrownBy(() -> accounts.byAdmissionNumber(number)).isInstanceOf(BillingException.AccountNotFound.class);
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
