package com.ClinicaDeYmid.commons.security;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
class AuthUsersTopicReaderIT {

    private static final String TOPIC = "auth.users.v1";

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1");

    @Test
    void loadsTheLatestAccessStateOfEveryStaffMemberAndKeepsFollowingTheTopic() throws Exception {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 3, (short) 1))).all().get();
        }
        UUID nurse = UUID.randomUUID();
        UUID doctor = UUID.randomUUID();
        Instant revokedAt = Instant.parse("2026-09-15T12:00:00.123456Z");
        publish(nurse, 1, "ACTIVE", Instant.parse("2026-09-01T08:00:00Z"));
        publish(nurse, 2, "SUSPENDED", revokedAt);
        publish(doctor, 4, "ACTIVE", revokedAt);
        StaffAccessRegistry registry = new StaffAccessRegistry();
        AuthUsersTopicReader reader = new AuthUsersTopicReader(registry, TOPIC, KAFKA.getBootstrapServers(), "probe-service");

        reader.start();
        try {
            await().atMost(Duration.ofSeconds(30)).until(registry::caughtUp);
            assertThat(registry.revoked(nurse, Instant.now())).isTrue();
            assertThat(registry.revoked(doctor, revokedAt.minusSeconds(1))).isTrue();
            assertThat(registry.revoked(doctor, revokedAt.plusSeconds(1))).isFalse();
            assertThat(registry.revoked(UUID.randomUUID(), Instant.now())).isFalse();

            publish(doctor, 5, "DEACTIVATED", revokedAt.plusSeconds(10));
            await().atMost(Duration.ofSeconds(10)).until(() -> registry.revoked(doctor, Instant.now()));
            publish(doctor, 3, "ACTIVE", revokedAt);
            Thread.sleep(1500);
            assertThat(registry.of(doctor)).map(StaffAccessRegistry.StaffAccess::status).contains("DEACTIVATED");
        } finally {
            reader.stop();
        }
    }

    private static void publish(UUID user, long version, String status, Instant tokensNotBefore) throws Exception {
        String event = """
                {"type":"UserSuspended","schemaVersion":1,"userUuid":"%s","userVersion":%d,
                 "data":{"user":{"uuid":"%s","status":"%s","tokensNotBefore":"%s"}}}""".formatted(user, version, user, status, tokensNotBefore);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()))) {
            producer.send(new ProducerRecord<>(TOPIC, user.toString(), event)).get();
        }
    }
}
