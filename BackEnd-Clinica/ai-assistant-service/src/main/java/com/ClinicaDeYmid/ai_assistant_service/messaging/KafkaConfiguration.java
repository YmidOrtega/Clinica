package com.ClinicaDeYmid.ai_assistant_service.messaging;

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

    static final String DEAD_LETTER_SUFFIX = ".assistant.dlt";
    static final String INVOICE_DEAD_LETTER_TOPIC = InvoiceEventsListener.TOPIC + DEAD_LETTER_SUFFIX;
    static final String FILING_DEAD_LETTER_TOPIC = DeadlineAlertsListener.FILING_TOPIC + DEAD_LETTER_SUFFIX;
    static final String OBJECTION_DEAD_LETTER_TOPIC = DeadlineAlertsListener.OBJECTION_TOPIC + DEAD_LETTER_SUFFIX;

    @Bean
    NewTopic invoiceEventsDeadLetterTopic() {
        return TopicBuilder.name(INVOICE_DEAD_LETTER_TOPIC).partitions(3).build();
    }

    @Bean
    NewTopic filingAlertsDeadLetterTopic() {
        return TopicBuilder.name(FILING_DEAD_LETTER_TOPIC).partitions(3).build();
    }

    @Bean
    NewTopic objectionAlertsDeadLetterTopic() {
        return TopicBuilder.name(OBJECTION_DEAD_LETTER_TOPIC).partitions(3).build();
    }

    @Bean
    DefaultErrorHandler assistantEventsErrorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, failure) -> new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, record.partition()));
        ExponentialBackOff backOff = new ExponentialBackOff(500, 2.0);
        backOff.setMaxAttempts(4);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(MalformedInvoiceEventException.class, IllegalArgumentException.class);
        return handler;
    }
}
