package com.ClinicaDeYmid.billing_service.infrastructure.events;

import com.ClinicaDeYmid.billing_service.application.filing.FilingDeadlineAlerts;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "clinica.billing.filing.alerts.enabled", havingValue = "true", matchIfMissing = true)
class FilingDeadlineSchedule {

    private final FilingDeadlineAlerts alerts;

    FilingDeadlineSchedule(FilingDeadlineAlerts alerts) {
        this.alerts = alerts;
    }

    @Scheduled(cron = "${clinica.billing.filing.alerts.cron:0 0 6 * * *}",
            zone = "${clinica.billing.time-zone}")
    void alert() {
        alerts.run();
    }
}
