package com.ClinicaDeYmid.billing_service.infrastructure.events;

import com.ClinicaDeYmid.billing_service.application.objection.ObjectionDeadlineAlerts;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "clinica.billing.objections.alerts.enabled", havingValue = "true", matchIfMissing = true)
class ObjectionDeadlineSchedule {

    private final ObjectionDeadlineAlerts alerts;

    ObjectionDeadlineSchedule(ObjectionDeadlineAlerts alerts) {
        this.alerts = alerts;
    }

    @Scheduled(cron = "${clinica.billing.objections.alerts.cron:0 5 6 * * *}", zone = "${clinica.billing.time-zone}")
    void alert() {
        alerts.run();
    }
}
