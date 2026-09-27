package com.ClinicaDeYmid.billing_service.infrastructure.messaging;

import com.ClinicaDeYmid.billing_service.application.EpisodeAccountProjection;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class AdmissionEventsListener {

    static final String TOPIC = "admissions.events.v1";

    private static final Logger log = LoggerFactory.getLogger(AdmissionEventsListener.class);

    private final EpisodeAccountProjection projection;

    AdmissionEventsListener(EpisodeAccountProjection projection) {
        this.projection = projection;
    }

    @KafkaListener(id = "admission-events", idIsGroup = false, topics = TOPIC,
            autoStartup = "${clinica.billing.admission-events.enabled:true}")
    void onAdmissionEvent(ConsumerRecord<String, String> record) {
        AdmissionEventMapper.toSnapshot(record.value()).ifPresentOrElse(projection::follow,
                () -> log.debug("Ignored admission event of an unknown type at offset {}", record.offset()));
    }
}
