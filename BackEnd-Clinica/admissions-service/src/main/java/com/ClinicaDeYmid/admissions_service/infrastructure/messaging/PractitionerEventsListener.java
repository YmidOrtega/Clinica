package com.ClinicaDeYmid.admissions_service.infrastructure.messaging;

import com.ClinicaDeYmid.admissions_service.application.practitioner.PractitionerDirectory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class PractitionerEventsListener {

    static final String TOPIC = "practitioners.v1";

    private static final Logger log = LoggerFactory.getLogger(PractitionerEventsListener.class);

    private final PractitionerDirectory directory;

    PractitionerEventsListener(PractitionerDirectory directory) {
        this.directory = directory;
    }

    @KafkaListener(id = "practitioner-events", idIsGroup = false, topics = TOPIC,
            autoStartup = "${clinica.admissions.practitioner-events.enabled:true}")
    void onPractitionerEvent(ConsumerRecord<String, String> record) {
        PractitionerEventMapper.toReference(record.value()).ifPresentOrElse(directory::apply,
                () -> log.debug("Ignored a tombstone at offset {}", record.offset()));
    }
}
