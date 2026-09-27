package com.ClinicaDeYmid.billing_service.infrastructure.messaging;

import com.ClinicaDeYmid.billing_service.application.clinical.ClinicalProjection;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class ClinicalEventsListener {

    static final String TOPIC = "clinical.encounters.v1";

    private static final Logger log = LoggerFactory.getLogger(ClinicalEventsListener.class);

    private final ClinicalProjection projection;

    ClinicalEventsListener(ClinicalProjection projection) {
        this.projection = projection;
    }

    @KafkaListener(id = "clinical-events", idIsGroup = false, topics = TOPIC,
            autoStartup = "${clinica.billing.clinical-events.enabled:true}")
    void onClinicalEvent(ConsumerRecord<String, String> record) {
        ClinicalEventMapper.toFact(record.value()).ifPresentOrElse(projection::follow,
                () -> log.debug("Ignored clinical event of an unknown type at offset {}", record.offset()));
    }
}
