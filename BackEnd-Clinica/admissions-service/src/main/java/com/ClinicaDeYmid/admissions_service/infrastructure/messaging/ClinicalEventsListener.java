package com.ClinicaDeYmid.admissions_service.infrastructure.messaging;

import com.ClinicaDeYmid.admissions_service.application.TriageReflection;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class ClinicalEventsListener {

    static final String TOPIC = "clinical.encounters.v1";

    private static final Logger log = LoggerFactory.getLogger(ClinicalEventsListener.class);

    private final TriageReflection triage;

    ClinicalEventsListener(TriageReflection triage) {
        this.triage = triage;
    }

    @KafkaListener(id = "clinical-events", topics = TOPIC,
            autoStartup = "${clinica.admissions.clinical-events.enabled:true}")
    void onClinicalEvent(ConsumerRecord<String, String> record) {
        ClinicalEventMapper.toTriage(record.value()).ifPresentOrElse(
                reflected -> triage.apply(reflected.admissionUuid(), reflected.level(), reflected.at(),
                        reflected.clinician()),
                () -> log.debug("Ignored a clinical event without triage at offset {}", record.offset()));
    }
}
