package com.ClinicaDeYmid.billing_service.infra.feignclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "admissions-service", contextId = "admissionsAttentionClient",
        path = "/api/v1/attentions")
public interface AdmissionsAttentionClient {

    @PatchMapping("/{id}/mark-invoiced")
    void markAsInvoiced(
            @PathVariable("id") Long attentionId,
            @RequestParam("saleOrderId") Long saleOrderId);
}
