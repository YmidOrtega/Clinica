package com.ClinicaDeYmid.practitioners_service.web;

import com.ClinicaDeYmid.commons.security.RecentAuthentication;
import com.ClinicaDeYmid.practitioners_service.service.FeeService;
import com.ClinicaDeYmid.practitioners_service.service.FeeViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(FeeController.BASE_PATH)
@Tag(name = "Fees", description = "Honorarios pactados con el profesional; nunca se reescriben")
class FeeController {

    static final String BASE_PATH = "/api/v1/practitioners/{practitionerUuid}/fee-agreements";

    private final FeeService fees;
    private final RecentAuthentication recentAuthentication;

    FeeController(FeeService fees, RecentAuthentication recentAuthentication) {
        this.fees = fees;
        this.recentAuthentication = recentAuthentication;
    }

    @PostMapping
    @PreAuthorize(Access.MANAGE_FEES)
    @Operation(summary = "Pactar honorarios, revocando el acuerdo vigente desde la nueva fecha",
            description = "Exige segundo factor reciente")
    ResponseEntity<FeeResponses.AgreementView> agree(@PathVariable UUID practitionerUuid,
                                                     @RequestBody FeeRequests.NewAgreement request) {
        recentAuthentication.require();
        FeeViews.AgreementView agreed = fees.agree(practitionerUuid, request.toCommand());
        return ResponseEntity
                .created(URI.create("/api/v1/practitioners/" + practitionerUuid + "/fee-agreements/" + agreed.uuid()))
                .body(FeeResponses.AgreementView.from(agreed));
    }

    @GetMapping
    @PreAuthorize(Access.MANAGE_FEES)
    @Operation(summary = "Historial completo de honorarios del profesional")
    List<FeeResponses.AgreementView> history(@PathVariable UUID practitionerUuid) {
        return fees.history(practitionerUuid).stream().map(FeeResponses.AgreementView::from).toList();
    }

    @GetMapping("/in-force")
    @PreAuthorize(Access.READ_FEES)
    @Operation(summary = "Honorarios vigentes en una fecha",
            description = "Facturación lo lee para calcular los honorarios por pagar de cada procedimiento")
    ResponseEntity<FeeResponses.AgreementView> inForce(@PathVariable UUID practitionerUuid,
                                                       @RequestParam(required = false)
                                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
        return fees.inForceOn(practitionerUuid, on)
                .map(agreement -> ResponseEntity.ok(FeeResponses.AgreementView.from(agreement)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
