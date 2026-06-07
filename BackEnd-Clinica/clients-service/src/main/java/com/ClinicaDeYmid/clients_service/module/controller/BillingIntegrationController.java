package com.ClinicaDeYmid.clients_service.module.controller;

import com.ClinicaDeYmid.clients_service.infra.exception.ContractNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import com.ClinicaDeYmid.clients_service.module.dto.ContractTariffDto;
import com.ClinicaDeYmid.clients_service.module.dto.PortfolioPriceDto;
import com.ClinicaDeYmid.clients_service.module.entity.Contract;
import com.ClinicaDeYmid.clients_service.module.entity.Portfolio;
import com.ClinicaDeYmid.clients_service.module.repository.PortfolioRepository;
import com.ClinicaDeYmid.clients_service.module.service.GetContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@Tag(name = "Billing Integration", description = "Endpoints internos para integración con billing-service")
@SecurityRequirement(name = "Bearer Authentication")
public class BillingIntegrationController {

    private final PortfolioRepository portfolioRepository;
    private final GetContractService contractService;

    @GetMapping("/api/v1/portfolios/{id}")
    @Operation(summary = "Obtener portafolio por ID con precio base",
            description = "Uso interno: billing-service consulta el precio base de un servicio del portafolio.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Portafolio encontrado"),
            @ApiResponse(responseCode = "404", description = "Portafolio no encontrado")
    })
    public ResponseEntity<PortfolioPriceDto> getPortfolioById(@PathVariable @Min(1) Long id) {
        log.info("Consulta interna de portafolio ID: {}", id);
        Portfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Portafolio con ID " + id + " no encontrado"));
        return ResponseEntity.ok(new PortfolioPriceDto(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getCodeCups(),
                portfolio.getCodeClinic(),
                portfolio.getPrice()
        ));
    }

    @GetMapping("/api/v1/contracts/{id}")
    @Operation(summary = "Obtener contrato por ID con tarifa acordada",
            description = "Uso interno: billing-service consulta la tarifa acordada de un contrato.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contrato encontrado"),
            @ApiResponse(responseCode = "404", description = "Contrato no encontrado")
    })
    public ResponseEntity<ContractTariffDto> getContractById(@PathVariable @Min(1) Long id) {
        log.info("Consulta interna de contrato ID: {}", id);
        Contract contract = contractService.getEntityContractById(id);
        return ResponseEntity.ok(new ContractTariffDto(
                contract.getId(),
                contract.getContractNumber(),
                contract.getAgreedTariff(),
                contract.getStatus()
        ));
    }
}
