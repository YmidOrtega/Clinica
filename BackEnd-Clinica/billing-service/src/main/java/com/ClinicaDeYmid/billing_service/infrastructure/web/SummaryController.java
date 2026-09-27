package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.application.AccountSummaries;
import com.ClinicaDeYmid.billing_service.application.UncontractedProposals;
import com.ClinicaDeYmid.billing_service.domain.AccountSummary;
import com.ClinicaDeYmid.billing_service.domain.Copayment;
import com.ClinicaDeYmid.billing_service.domain.CoveragePlan;
import com.ClinicaDeYmid.billing_service.domain.PackageCharge;
import com.ClinicaDeYmid.billing_service.domain.PatientShareAdjustment;
import com.ClinicaDeYmid.billing_service.domain.Sale;
import com.ClinicaDeYmid.billing_service.domain.UncontractedCare;
import com.ClinicaDeYmid.commons.security.AuthenticatedUser;
import com.ClinicaDeYmid.commons.security.RecentAuthentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@Tag(name = "Resumen de cuenta", description = "Qué se factura, a quién y en qué unidades")
class SummaryController {

    private static final Logger log = LoggerFactory.getLogger(SummaryController.class);

    private final AccountSummaries summaries;
    private final UncontractedProposals proposals;
    private final RecentAuthentication recentAuthentication;

    SummaryController(AccountSummaries summaries, UncontractedProposals proposals,
                      RecentAuthentication recentAuthentication) {
        this.summaries = summaries;
        this.proposals = proposals;
        this.recentAuthentication = recentAuthentication;
    }

    @GetMapping(AccountController.BASE_PATH + "/{admissionNumber}/summary")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Resumen de la cuenta por unidad facturable",
            description = "Ambulatorio: una unidad por venta confirmada. Urgencias y hospitalización: la cuenta entera, "
                    + "lista tras el egreso. Cada paquete se cobra una vez por cuenta; el copago sale de las "
                    + "autorizaciones usadas o de una corrección auditada. Si el episodio tiene pagador pero no "
                    + "contrato, propone el motivo y la cobertura para facturarle sin contrato")
    SummaryView summary(@PathVariable @Pattern(regexp = "^ADM-[0-9]{4}-[0-9]{6}$") String admissionNumber) {
        AccountSummaries.Context context = summaries.context(admissionNumber);
        return SummaryView.from(context.summary(), proposals.of(context).map(ProposalView::from).orElse(null));
    }

    @PostMapping(AccountController.BASE_PATH + "/{admissionNumber}/patient-share-adjustments")
    @PreAuthorize(Access.INVOICE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Corregir a mano lo que paga el paciente en una unidad facturable",
            description = "Exige un segundo factor reciente y un motivo; la corrección más reciente es la que vale")
    AdjustmentView adjust(@PathVariable @Pattern(regexp = "^ADM-[0-9]{4}-[0-9]{6}$") String admissionNumber,
                          @Valid @RequestBody Adjustment request) {
        AuthenticatedUser user = recentAuthentication.require();
        log.info("Step-up accepted for a patient share adjustment: {} authenticated at {}", user.uuid(),
                user.authenticatedAt());
        return AdjustmentView.from(summaries.adjust(admissionNumber, request.saleUuid(), request.amount(),
                request.reason()));
    }

    record Adjustment(UUID saleUuid, @NotNull BigDecimal amount, @NotBlank String reason) {
    }

    record AdjustmentView(UUID uuid, UUID saleUuid, BigDecimal amount, String reason, Instant createdAt,
                          String createdBy) {

        static AdjustmentView from(PatientShareAdjustment adjustment) {
            return adjustment == null ? null : new AdjustmentView(adjustment.uuid(), adjustment.saleUuid(),
                    adjustment.amount(), adjustment.reason(), adjustment.createdAt(), adjustment.createdBy());
        }
    }

    record CopaymentView(UUID authorizationUuid, String authorizationNumber, BigDecimal amount) {

        static CopaymentView from(Copayment copayment) {
            return new CopaymentView(copayment.authorizationUuid(), copayment.authorizationNumber(), copayment.amount());
        }
    }

    record UnitView(AccountSummary.UnitKind kind, UUID saleUuid, List<String> saleNumbers, boolean ready,
                    String notReadyReason, BigDecimal linesTotal, List<PackageCharge> packages,
                    List<PackageCharge> duplicatePackages, BigDecimal total, BigDecimal patientShare,
                    AccountSummary.ShareSource shareSource, List<CopaymentView> copayments, AdjustmentView adjustment,
                    BigDecimal payerShare) {

        static UnitView from(AccountSummary.Unit unit) {
            return new UnitView(unit.kind(), unit.saleUuid(), unit.sales().stream().map(Sale::number).toList(),
                    unit.ready(), unit.notReadyReason(), unit.linesTotal(), unit.packages(), unit.duplicatePackages(),
                    unit.total(), unit.patientShare(), unit.shareSource(),
                    unit.copayments().stream().map(CopaymentView::from).toList(), AdjustmentView.from(unit.adjustment()),
                    unit.payerShare());
        }
    }

    record ProposalView(UUID payerUuid, UncontractedCare reason, String reasonCode, CoveragePlan coverage,
                        String coverageCode) {

        static ProposalView from(UncontractedProposals.Proposal proposal) {
            return new ProposalView(proposal.payerUuid(), proposal.reason(),
                    proposal.reason() == null ? null : proposal.reason().sisproCode(), proposal.coverage(),
                    proposal.coverage() == null ? null : proposal.coverage().sisproCode());
        }
    }

    record SummaryView(String admissionNumber, String accountStatus, List<UnitView> units, BigDecimal total,
                       BigDecimal patientShare, BigDecimal payerShare, ProposalView uncontractedProposal) {

        static SummaryView from(AccountSummary summary, ProposalView proposal) {
            return new SummaryView(summary.account().admissionNumber(), summary.account().status().code().name(),
                    summary.units().stream().map(UnitView::from).toList(), summary.total(), summary.patientShare(),
                    summary.payerShare(), proposal);
        }
    }
}
