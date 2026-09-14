package com.ClinicaDeYmid.clinical_history_service.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration(proxyBeanMethods = false)
class KafkaConfiguration {

    static final String DEAD_LETTER_TOPIC = PatientEventsListener.TOPIC + ".clinical-history.dlt";

    @Bean
    NewTopic patientEventsDeadLetterTopic() {
        return TopicBuilder.name(DEAD_LETTER_TOPIC).partitions(3).build();
    }

    @Bean
    DefaultErrorHandler patientEventsErrorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, failure) -> new TopicPartition(DEAD_LETTER_TOPIC, record.partition()));
        ExponentialBackOff backOff = new ExponentialBackOff(500, 2.0);
        backOff.setMaxAttempts(4);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(MalformedPatientEventException.class, IllegalArgumentException.class);
        return handler;
    }
}
