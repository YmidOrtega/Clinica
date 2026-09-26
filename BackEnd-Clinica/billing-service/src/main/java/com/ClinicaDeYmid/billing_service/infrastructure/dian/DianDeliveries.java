package com.ClinicaDeYmid.billing_service.infrastructure.dian;

import com.ClinicaDeYmid.billing_service.application.DianDelivery;
import com.ClinicaDeYmid.billing_service.application.DocumentAttachment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "clinica.billing.dian.delivery.enabled", havingValue = "true", matchIfMissing = true)
class DianDeliveries {

    private final DianDelivery delivery;
    private final DocumentAttachment attachment;

    DianDeliveries(DianDelivery delivery, DocumentAttachment attachment) {
        this.delivery = delivery;
        this.attachment = attachment;
    }

    @Scheduled(initialDelayString = "${clinica.billing.dian.delivery.delay:PT30S}",
            fixedDelayString = "${clinica.billing.dian.delivery.delay:PT30S}")
    void deliverAndCheck() {
        delivery.deliverPending(20);
        delivery.checkPending(20);
        attachment.attachPending(20);
    }
}
