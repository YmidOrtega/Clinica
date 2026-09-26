package com.ClinicaDeYmid.billing_service.infrastructure.dian;

import com.ClinicaDeYmid.billing_service.application.InvoiceSigning;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "clinica.billing.dian.signature-retries.enabled", havingValue = "true", matchIfMissing = true)
class SignatureRetries {

    private final InvoiceSigning signing;

    SignatureRetries(InvoiceSigning signing) {
        this.signing = signing;
    }

    @Scheduled(initialDelayString = "${clinica.billing.dian.signature-retries.delay:PT1M}",
            fixedDelayString = "${clinica.billing.dian.signature-retries.delay:PT1M}")
    void signPending() {
        signing.signPending(50);
    }
}
