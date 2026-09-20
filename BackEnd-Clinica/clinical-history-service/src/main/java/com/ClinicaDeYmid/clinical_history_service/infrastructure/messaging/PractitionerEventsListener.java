package com.ClinicaDeYmid.clinical_history_service.infrastructure.messaging;

import com.ClinicaDeYmid.clinical_history_service.application.practitioner.PractitionerReferenceProjection;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class PractitionerEventsListener {

    static final String TOPIC = "practitioners.v1";

    private final PractitionerReferenceProjection projection;

    PractitionerEventsListener(PractitionerReferenceProjection projection) {
        this.projection = projection;
    }

    @KafkaListener(id = "practitioner-events", topics = TOPIC,
            autoStartup = "${clinica.clinical.practitioner-events.enabled:true}")
    void onPractitionerEvent(ConsumerRecord<String, String> record) {
        if (record.value() == null) {
            return;
        }
        PractitionerEventMapper.toReference(record.value()).ifPresentOrElse(projection::apply,
                () -> PractitionerEventMapper.practitionerWithoutAccount(record.value())
                        .ifPresent(projection::accountUnlinkedFrom));
    }
}
