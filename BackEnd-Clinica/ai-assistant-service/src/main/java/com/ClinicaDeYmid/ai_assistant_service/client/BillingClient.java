package com.ClinicaDeYmid.ai_assistant_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

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

    @PostMapping("/api/v1/billing/invoices/{uuid}/signature")
    String sign(@PathVariable("uuid") UUID uuid);

    @PostMapping("/api/v1/billing/invoices/{uuid}/dian-delivery")
    String sendToDian(@PathVariable("uuid") UUID uuid);

    @PostMapping("/api/v1/billing/invoices/{uuid}/rips-validation")
    String validateRips(@PathVariable("uuid") UUID uuid);

    @PostMapping(value = "/api/v1/billing/invoices/{uuid}/filing", consumes = MediaType.APPLICATION_JSON_VALUE)
    String file(@PathVariable("uuid") UUID uuid, @RequestBody String registration);

    @GetMapping("/api/v1/billing/objections/{uuid}")
    ResponseEntity<String> objection(@PathVariable("uuid") UUID uuid);

    @PostMapping(value = "/api/v1/billing/objections/{uuid}/response", consumes = MediaType.APPLICATION_JSON_VALUE)
    String answerObjection(@PathVariable("uuid") UUID uuid, @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
                           @RequestBody String response);
}
