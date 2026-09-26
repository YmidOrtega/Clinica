package com.ClinicaDeYmid.billing_service.infrastructure.messaging;

import com.ClinicaDeYmid.billing_service.domain.BillingException;
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

    static final String DEAD_LETTER_SUFFIX = ".billing.dlt";
    static final String ADMISSION_DEAD_LETTER_TOPIC = AdmissionEventsListener.TOPIC + DEAD_LETTER_SUFFIX;
    static final String CLINICAL_DEAD_LETTER_TOPIC = ClinicalEventsListener.TOPIC + DEAD_LETTER_SUFFIX;

    @Bean
    NewTopic admissionEventsDeadLetterTopic() {
        return TopicBuilder.name(ADMISSION_DEAD_LETTER_TOPIC).partitions(3).build();
    }

    @Bean
    NewTopic clinicalEventsDeadLetterTopic() {
        return TopicBuilder.name(CLINICAL_DEAD_LETTER_TOPIC).partitions(3).build();
    }

    @Bean
    DefaultErrorHandler billingEventsErrorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, failure) -> new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, record.partition()));
        ExponentialBackOff backOff = new ExponentialBackOff(500, 2.0);
        backOff.setMaxAttempts(4);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(MalformedAdmissionEventException.class, MalformedClinicalEventException.class, BillingException.class,
                IllegalArgumentException.class);
        return handler;
    }
}
