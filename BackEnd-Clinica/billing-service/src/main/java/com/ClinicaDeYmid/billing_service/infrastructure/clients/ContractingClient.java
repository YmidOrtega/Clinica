package com.ClinicaDeYmid.billing_service.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "contracting-service")
interface ContractingClient {

    @GetMapping("/api/v1/payers/{uuid}")
    PayerPayload payer(@PathVariable("uuid") UUID uuid);

    @GetMapping("/api/v1/portfolio-items/{uuid}")
    PortfolioItemPayload portfolioItem(@PathVariable("uuid") UUID uuid);

    @PostMapping("/api/v1/portfolio-items/search")
    PortfolioPage searchPortfolio(@RequestBody PortfolioSearch search, @RequestParam("size") int size);

    record PortfolioSearch(String cupsCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PortfolioPage(List<PortfolioItemPayload> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PortfolioItemPayload(UUID uuid, String cupsCode, String clinicCode, String name, String category,
                                Status status) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Status(String code, boolean offered) {
        }

        boolean offered() {
            return status != null && status.offered();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PayerPayload(UUID uuid, String socialReason, String nit, String type) {
    }
}
