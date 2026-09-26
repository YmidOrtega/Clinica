package com.ClinicaDeYmid.billing_service.infrastructure.ministry;

import com.ClinicaDeYmid.billing_service.application.rips.MinistryValidation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "clinica.billing.ministry.retries.enabled", havingValue = "true", matchIfMissing = true)
class MinistryValidationRetries {

    private final MinistryValidation validation;

    MinistryValidationRetries(MinistryValidation validation) {
        this.validation = validation;
    }

    @Scheduled(initialDelayString = "${clinica.billing.ministry.retries.delay:PT5M}",
            fixedDelayString = "${clinica.billing.ministry.retries.delay:PT5M}")
    void retryPending() {
        validation.retryPending(20);
    }
}
