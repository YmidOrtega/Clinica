package com.ClinicaDeYmid.clinical_history_service.infrastructure.messaging;

import com.ClinicaDeYmid.clinical_history_service.application.patient.PatientReferenceProjection;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class PatientEventsListener {

    static final String TOPIC = "patient.events.v1";

    private static final Logger log = LoggerFactory.getLogger(PatientEventsListener.class);

    private final PatientReferenceProjection projection;

    PatientEventsListener(PatientReferenceProjection projection) {
        this.projection = projection;
    }

    @KafkaListener(id = "patient-events", topics = TOPIC, autoStartup = "${clinica.clinical.patient-events.enabled:true}")
    void onPatientEvent(ConsumerRecord<String, String> record) {
        PatientEventMapper.toReference(record.value()).ifPresentOrElse(projection::apply,
                () -> log.debug("Ignored patient event of an unknown type at offset {}", record.offset()));
    }
}
