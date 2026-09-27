package com.ClinicaDeYmid.ai_assistant_service.messaging;

import com.ClinicaDeYmid.ai_assistant_service.service.FindingService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class DeadlineAlertsListener {

    static final String FILING_TOPIC = "billing.filing-deadlines.v1";
    static final String OBJECTION_TOPIC = "billing.claim-objections.v1";

    private final FindingService findings;

    DeadlineAlertsListener(FindingService findings) {
        this.findings = findings;
    }

    @KafkaListener(id = "filing-alerts", idIsGroup = false, topics = FILING_TOPIC,
            autoStartup = "${clinica.assistant.invoice-events.enabled:true}")
    void onFilingAlert(ConsumerRecord<String, String> record) {
        if (record.value() != null) {
            findings.raise(DeadlineAlertMapper.filing(record.value()));
        }
    }

    @KafkaListener(id = "objection-alerts", idIsGroup = false, topics = OBJECTION_TOPIC,
            autoStartup = "${clinica.assistant.invoice-events.enabled:true}")
    void onObjectionAlert(ConsumerRecord<String, String> record) {
        if (record.value() != null) {
            findings.raise(DeadlineAlertMapper.objection(record.value()));
        }
    }
}
