package com.ClinicaDeYmid.ai_assistant_service.messaging;

import com.ClinicaDeYmid.ai_assistant_service.service.InvoiceProjection;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class InvoiceEventsListener {

    static final String TOPIC = "billing.invoices.v1";

    private static final Logger log = LoggerFactory.getLogger(InvoiceEventsListener.class);

    private final InvoiceProjection projection;

    InvoiceEventsListener(InvoiceProjection projection) {
        this.projection = projection;
    }

    @KafkaListener(id = "invoice-events", idIsGroup = false, topics = TOPIC,
            autoStartup = "${clinica.assistant.invoice-events.enabled:true}")
    void onInvoiceEvent(ConsumerRecord<String, String> record) {
        InvoiceEventMapper.toState(record.value()).ifPresentOrElse(projection::follow,
                () -> log.debug("Ignored a tombstone at offset {}", record.offset()));
    }
}
