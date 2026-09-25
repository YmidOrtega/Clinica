package com.ClinicaDeYmid.contracting_service.infrastructure.web;

import com.ClinicaDeYmid.contracting_service.application.PricingQueries;
import com.ClinicaDeYmid.contracting_service.infrastructure.web.PricingResponses.QuoteView;
import com.ClinicaDeYmid.contracting_service.infrastructure.web.PricingResponses.SurgicalQuoteView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/price-quotes")
@Tag(name = "Pricing", description = "Resolución de precios de un contrato en una fecha")
class PricingController {

    private final PricingQueries pricing;

    PricingController(PricingQueries pricing) {
        this.pricing = pricing;
    }

    @PostMapping
    @PreAuthorize(Access.QUOTE_PRICES)
    @Operation(summary = "Resolver el precio de unos servicios para un contrato y una fecha",
            description = "Determinista: primero los paquetes pactados, luego las excepciones del contrato y por último el manual "
                    + "con su factor. La respuesta trae la referencia de cada precio para que la factura pueda explicarse después")
    QuoteView quote(@Valid @RequestBody PricingRequests.Quote request) {
        return QuoteView.from(pricing.quote(request.contractUuid(), request.on(), request.toRequested()));
    }

    @PostMapping("/surgical")
    @PreAuthorize(Access.QUOTE_PRICES)
    @Operation(summary = "Liquidar un acto quirúrgico por componentes",
            description = "El procedimiento de mayor base va al 100 % y los demás al porcentaje de misma vía o de vía "
                    + "diferente que fija la versión del manual. Paquetes, capitación y excepciones pactadas se "
                    + "respetan antes que el manual")
    SurgicalQuoteView surgicalQuote(@Valid @RequestBody PricingRequests.SurgicalQuote request) {
        return SurgicalQuoteView.from(pricing.surgicalQuote(request.contractUuid(), request.on(), request.toRequested()));
    }
}
