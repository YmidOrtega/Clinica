package com.ClinicaDeYmid.ai_assistant_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "billing-service")
interface BillingClient {

    @GetMapping("/api/v1/billing/invoices/{uuid}")
    String invoice(@PathVariable("uuid") UUID uuid);

    @GetMapping("/api/v1/billing/invoices/{uuid}/dian-verdicts")
    String dianVerdicts(@PathVariable("uuid") UUID uuid);

    @GetMapping("/api/v1/billing/invoices/{uuid}/rips-validations")
    String ripsValidations(@PathVariable("uuid") UUID uuid);

    @GetMapping("/api/v1/billing/invoices/{uuid}/filing")
    String filing(@PathVariable("uuid") UUID uuid);

    @GetMapping("/api/v1/billing/invoices/{uuid}/objections")
    String objections(@PathVariable("uuid") UUID uuid);

    @GetMapping("/api/v1/billing/invoices/{uuid}/credit-notes")
    String creditNotes(@PathVariable("uuid") UUID uuid);
}
