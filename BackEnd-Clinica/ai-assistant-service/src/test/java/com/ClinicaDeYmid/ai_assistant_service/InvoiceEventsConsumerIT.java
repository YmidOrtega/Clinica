package com.ClinicaDeYmid.ai_assistant_service;

import com.ClinicaDeYmid.ai_assistant_service.repository.InvoiceSnapshotRepository;
import com.ClinicaDeYmid.ai_assistant_service.support.InvoiceEvents;
import com.ClinicaDeYmid.ai_assistant_service.support.JwtTestTokens;
import com.ClinicaDeYmid.ai_assistant_service.support.PostgresTestContainer;
import com.ClinicaDeYmid.ai_assistant_service.support.ProducerContract;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(PostgresTestContainer.class)
class InvoiceEventsConsumerIT {

    private static final String TOPIC = "billing.invoices.v1";
    private static final String DLT = "billing.invoices.v1.assistant.dlt";

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

    private static KafkaProducer<String, String> producer;

    @Autowired
    private InvoiceSnapshotRepository invoices;

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
        JwtTestTokens.register(registry);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.admin.auto-create", () -> true);
        registry.add("clinica.assistant.invoice-events.enabled", () -> true);
    }

    @Test
    void theFixturesFollowTheContractPublishedByBilling() {
        Instant now = Instant.now();
        assertThat(ProducerContract.INVOICE_EVENTS.breaches(InvoiceEvents.issuedToThePayer(UUID.randomUUID(), "SETP1", now))).isEmpty();
        assertThat(ProducerContract.INVOICE_EVENTS.breaches(InvoiceEvents.rejectedByTheDian(UUID.randomUUID(), "SETP1", now))).isEmpty();
        assertThat(ProducerContract.INVOICE_EVENTS.breaches(InvoiceEvents.uncontractedWithShortfall(UUID.randomUUID(), "SETP1", now))).isEmpty();
    }

    @Test
    void followsEachInvoiceToItsLatestStateAndIgnoresOlderOnes() throws Exception {
        UUID invoice = UUID.randomUUID();
        Instant issued = Instant.parse("2026-09-01T15:00:00Z");

        publish(invoice, InvoiceEvents.rejectedByTheDian(invoice, "SETP990000101", issued.plusSeconds(60)));
        publish(invoice, InvoiceEvents.issuedToThePayer(invoice, "SETP990000101", issued));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(invoices.findByInvoiceUuid(invoice)).get().satisfies(snapshot -> {
                    assertThat(snapshot.lastEventType()).isEqualTo("InvoiceRejectedByDian");
                    assertThat(snapshot.dianStatus()).isEqualTo("REJECTED");
                    assertThat(snapshot.payerNit()).isEqualTo(InvoiceEvents.PAYER_NIT);
                }));
        Thread.sleep(1500);
        assertThat(invoices.findByInvoiceUuid(invoice).orElseThrow().lastEventType()).isEqualTo("InvoiceRejectedByDian");
    }

    @Test
    void keepsWhatTheRulesNeedAboutContractAndCopay() throws Exception {
        UUID invoice = UUID.randomUUID();

        publish(invoice, InvoiceEvents.uncontractedWithShortfall(invoice, "SETP990000102", Instant.now()));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(invoices.findByInvoiceUuid(invoice)).get().satisfies(snapshot -> {
                    assertThat(snapshot.uncontractedCare()).isEqualTo("EMERGENCY");
                    assertThat(snapshot.shareShortfall()).isEqualByComparingTo("3500");
                    assertThat(snapshot.state()).contains("\"objections\"");
                }));
    }

    @Test
    void anUnreadableEventGoesToTheDeadLetterTopicWithoutStoppingTheRest() throws Exception {
        UUID broken = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();

        publish(broken, "{\"invoiceUuid\":\"" + broken + "\",\"type\":\"InvoiceIssued\"}");
        publish(healthy, InvoiceEvents.issuedToThePayer(healthy, "SETP990000103", Instant.now()));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(invoices.findByInvoiceUuid(healthy)).isPresent());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-reader-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()))) {
            consumer.subscribe(List.of(DLT));
            await().atMost(Duration.ofSeconds(20)).until(() -> consumer.poll(Duration.ofMillis(500)).records(DLT)
                    .iterator().hasNext());
        }
        assertThat(invoices.findByInvoiceUuid(broken)).isEmpty();
    }

    private static void publish(UUID key, String value) throws Exception {
        producer.send(new ProducerRecord<>(TOPIC, key.toString(), value)).get();
    }
}
