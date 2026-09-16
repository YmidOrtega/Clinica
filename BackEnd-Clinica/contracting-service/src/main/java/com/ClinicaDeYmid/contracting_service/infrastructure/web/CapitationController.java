package com.ClinicaDeYmid.contracting_service.infrastructure.web;

import com.ClinicaDeYmid.commons.security.CurrentUser;
import com.ClinicaDeYmid.commons.security.RecentAuthentication;
import com.ClinicaDeYmid.contracting_service.application.CapitationCommands;
import com.ClinicaDeYmid.contracting_service.application.CapitationQueries;
import com.ClinicaDeYmid.contracting_service.infrastructure.web.CapitationResponses.AgreementView;
import com.ClinicaDeYmid.contracting_service.infrastructure.web.CapitationResponses.CoverageView;
import com.ClinicaDeYmid.contracting_service.infrastructure.web.CapitationResponses.ImportView;
import com.ClinicaDeYmid.contracting_service.infrastructure.web.CapitationResponses.MemberView;
import com.ClinicaDeYmid.contracting_service.infrastructure.web.CapitationResponses.VerificationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Capitation", description = "Acuerdos de capitación y presupuesto global, y la población capitada de cada contrato")
class CapitationController {

    private final CapitationCommands commands;
    private final CapitationQueries queries;
    private final RecentAuthentication recentAuthentication;
    private final CurrentUser currentUser;

    CapitationController(CapitationCommands commands, CapitationQueries queries, RecentAuthentication recentAuthentication,
                         CurrentUser currentUser) {
        this.commands = commands;
        this.queries = queries;
        this.recentAuthentication = recentAuthentication;
        this.currentUser = currentUser;
    }

    @PostMapping("/contracts/{uuid}/capitation-agreement")
    @PreAuthorize(Access.MANAGE_CAPITATION)
    @Operation(summary = "Pactar el valor per cápita del contrato",
            description = "Exige segundo factor reciente; el acuerdo anterior se revoca en la misma fecha. La liquidación mensual no vive en este servicio")
    AgreementView agreeCapitation(@PathVariable UUID uuid, @Valid @RequestBody CapitationRequests.CapitationAgreement request) {
        recentAuthentication.require();
        return AgreementView.from(commands.agreeCapitation(uuid, request.perCapitaValue(), request.periodicity(),
                request.technicalNote(), request.validFrom(), actor()));
    }

    @PostMapping("/contracts/{uuid}/budget-agreement")
    @PreAuthorize(Access.MANAGE_CAPITATION)
    @Operation(summary = "Pactar el techo presupuestal del contrato (PGP)")
    AgreementView agreeBudget(@PathVariable UUID uuid, @Valid @RequestBody CapitationRequests.BudgetAgreement request) {
        recentAuthentication.require();
        return AgreementView.from(commands.agreeGlobalBudget(uuid, request.budgetCeiling(), request.periodicity(),
                request.technicalNote(), request.validFrom(), actor()));
    }

    @GetMapping("/contracts/{uuid}/funding-agreements")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar los acuerdos de capitación o presupuesto del contrato")
    List<AgreementView> agreements(@PathVariable UUID uuid) {
        return queries.agreementsOf(uuid).stream().map(AgreementView::from).toList();
    }

    @PostMapping("/funding-agreements/{agreementUuid}/revocation")
    @PreAuthorize(Access.MANAGE_CAPITATION)
    @Operation(summary = "Revocar un acuerdo desde una fecha")
    AgreementView revoke(@PathVariable UUID agreementUuid, @RequestBody CapitationRequests.Revocation request) {
        recentAuthentication.require();
        return AgreementView.from(commands.revokeAgreement(agreementUuid, request.from(), actor()));
    }

    @PostMapping("/contracts/{uuid}/capitated-members/imports")
    @PreAuthorize(Access.MANAGE_CAPITATION)
    @Operation(summary = "Cargar la población capitada de un periodo",
            description = "Idempotente por documento dentro del periodo; cada afiliado se contrasta contra patient-service y queda sin verificar si el registro de pacientes no responde")
    ImportView importMembers(@PathVariable UUID uuid,
                             @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth period,
                             @Valid @RequestBody CapitationRequests.MemberImport request) {
        return ImportView.from(commands.importMembers(uuid, period, request.toMembers(), actor()));
    }

    @PostMapping("/contracts/{uuid}/capitated-members/verification")
    @PreAuthorize(Access.MANAGE_CAPITATION)
    @Operation(summary = "Reintentar la verificación de los afiliados que quedaron sin contrastar")
    VerificationView verify(@PathVariable UUID uuid,
                            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth period,
                            @RequestParam(defaultValue = "500") @Min(1) @Max(5000) int limit) {
        return VerificationView.from(commands.verify(uuid, period, limit));
    }

    @GetMapping("/contracts/{uuid}/capitated-members")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar la población capitada de un periodo")
    PagedModel<MemberView> members(@PathVariable UUID uuid,
                                   @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth period,
                                   @RequestParam(defaultValue = "0") @Min(0) int page,
                                   @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        CapitationQueries.Page found = queries.members(uuid, period, page, size);
        return new PagedModel<>(new PageImpl<>(found.members().stream().map(MemberView::from).toList(),
                PageRequest.of(page, size), found.total()));
    }

    @GetMapping("/capitated-members/coverage")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Saber si una persona está capitada en algún contrato",
            description = "Responde con los contratos que la tienen asignada en el periodo de la fecha consultada")
    List<CoverageView> coverage(@RequestParam String documentType, @RequestParam String documentNumber,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
        YearMonth period = YearMonth.from(on == null ? LocalDate.now() : on);
        return queries.coverage(documentType, documentNumber, period).stream().map(CoverageView::from).toList();
    }

    private String actor() {
        return currentUser.get().map(user -> user.uuid().toString()).orElse(null);
    }
}
