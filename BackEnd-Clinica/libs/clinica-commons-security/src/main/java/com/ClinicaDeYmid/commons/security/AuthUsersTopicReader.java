package com.ClinicaDeYmid.commons.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AuthUsersTopicReader implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AuthUsersTopicReader.class);
    private static final Duration POLL = Duration.ofSeconds(1);
    private static final Duration RETRY = Duration.ofSeconds(5);

    private final StaffAccessRegistry registry;
    private final String topic;
    private final Map<String, Object> consumerProperties;
    private final ObjectMapper json = new ObjectMapper();
    private volatile KafkaConsumer<String, String> consumer;
    private volatile boolean running;

    public AuthUsersTopicReader(StaffAccessRegistry registry, String topic, String bootstrapServers, String serviceName) {
        this.registry = registry;
        this.topic = topic;
        this.consumerProperties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.CLIENT_ID_CONFIG, serviceName + "-staff-revocations",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    }

    @Override
    public void start() {
        running = true;
        Thread.ofVirtual().name("staff-revocations").start(this::run);
    }

    @Override
    public void stop() {
        running = false;
        KafkaConsumer<String, String> current = consumer;
        if (current != null) {
            current.wakeup();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void run() {
        while (running) {
            try (KafkaConsumer<String, String> opened = new KafkaConsumer<>(consumerProperties)) {
                consumer = opened;
                List<TopicPartition> partitions = opened.partitionsFor(topic, Duration.ofSeconds(30)).stream()
                        .map(PartitionInfo::partition)
                        .map(partition -> new TopicPartition(topic, partition))
                        .toList();
                opened.assign(partitions);
                opened.seekToBeginning(partitions);
                Map<TopicPartition, Long> endOffsets = opened.endOffsets(partitions);
                while (running) {
                    opened.poll(POLL).forEach(this::apply);
                    if (!registry.caughtUp() && endOffsets.entrySet().stream().allMatch(end -> opened.position(end.getKey()) >= end.getValue())) {
                        registry.markCaughtUp();
                        log.info("Loaded the access state of {} staff members from {}", registry.size(), topic);
                    }
                }
            } catch (WakeupException stopping) {
                return;
            } catch (RuntimeException failure) {
                log.warn("Cannot read {}; tokens keep being validated with the {} staff states already known", topic, registry.size(), failure);
                sleep();
            }
        }
    }

    void apply(ConsumerRecord<String, String> record) {
        if (record.value() == null) {
            return;
        }
        try {
            JsonNode event = json.readTree(record.value());
            JsonNode user = event.path("data").path("user");
            registry.record(UUID.fromString(event.path("userUuid").asText()), new StaffAccessRegistry.StaffAccess(event.path("userVersion").asLong(),
                    user.path("status").asText(), Instant.parse(user.path("tokensNotBefore").asText())));
        } catch (Exception malformed) {
            log.warn("Ignored a malformed event at {}-{}@{}", record.topic(), record.partition(), record.offset());
        }
    }

    private void sleep() {
        try {
            Thread.sleep(RETRY);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
