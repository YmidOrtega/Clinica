package com.ClinicaDeYmid.billing_service.infrastructure.dian;

import com.ClinicaDeYmid.billing_service.application.DianDelivery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "clinica.billing.dian.delivery.enabled", havingValue = "true", matchIfMissing = true)
class DianDeliveries {

    private final DianDelivery delivery;

    DianDeliveries(DianDelivery delivery) {
        this.delivery = delivery;
    }

    @Scheduled(initialDelayString = "${clinica.billing.dian.delivery.delay:PT30S}",
            fixedDelayString = "${clinica.billing.dian.delivery.delay:PT30S}")
    void deliverAndCheck() {
        delivery.deliverPending(20);
        delivery.checkPending(20);
    }
}
