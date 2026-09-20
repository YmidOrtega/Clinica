package com.ClinicaDeYmid.admissions_service.infrastructure.messaging;

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

    static final String DEAD_LETTER_SUFFIX = ".admissions.dlt";
    static final String PATIENT_DEAD_LETTER_TOPIC = PatientEventsListener.TOPIC + DEAD_LETTER_SUFFIX;
    static final String PRACTITIONER_DEAD_LETTER_TOPIC = PractitionerEventsListener.TOPIC + DEAD_LETTER_SUFFIX;

    @Bean
    NewTopic patientEventsDeadLetterTopic() {
        return TopicBuilder.name(PATIENT_DEAD_LETTER_TOPIC).partitions(3).build();
    }

    @Bean
    NewTopic practitionerEventsDeadLetterTopic() {
        return TopicBuilder.name(PRACTITIONER_DEAD_LETTER_TOPIC).partitions(3).build();
    }

    @Bean
    DefaultErrorHandler admissionsEventsErrorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, failure) -> new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, record.partition()));
        ExponentialBackOff backOff = new ExponentialBackOff(500, 2.0);
        backOff.setMaxAttempts(4);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(MalformedPatientEventException.class,
                MalformedPractitionerEventException.class, IllegalArgumentException.class);
        return handler;
    }
}
