package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.application.DocumentSigning;
import com.ClinicaDeYmid.billing_service.application.SharedPaymentCommands;
import com.ClinicaDeYmid.billing_service.application.SharedPaymentQueries;
import com.ClinicaDeYmid.billing_service.domain.AccountSummary;
import com.ClinicaDeYmid.billing_service.domain.Copayment;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.SharedPaymentKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@Tag(name = "Pagos compartidos",
        description = "Copagos y cuotas que paga el paciente: se le facturan al recaudarlos y la factura al pagador los descuenta")
class SharedPaymentController {

    static final String SHARED_PAYMENTS = "/api/v1/billing/shared-payments";

    private final SharedPaymentCommands commands;
    private final SharedPaymentQueries queries;
    private final DocumentSigning signing;

    SharedPaymentController(SharedPaymentCommands commands, SharedPaymentQueries queries, DocumentSigning signing) {
        this.commands = commands;
        this.queries = queries;
        this.signing = signing;
    }

    @PostMapping(SHARED_PAYMENTS)
    @PreAuthorize(Access.COLLECT)
    @Operation(summary = "Facturar al paciente un copago o una cuota recién recaudada",
            description = "La llama el servicio de recaudo al recibir el pago. Emite y firma en el acto la factura "
                    + "electrónica al paciente (Concepto DIAN 012008 de 2026). Idempotente por collectionReference. "
                    + "Sin kind usa el tipo propuesto por la regla")
    ResponseEntity<SharedPaymentView> invoice(@Valid @RequestBody Collection request) {
        SharedPaymentCommands.Invoiced invoiced = commands.invoice(new SharedPaymentCommands.Collected(
                request.admissionNumber(), request.authorizationNumber(), request.kind(), request.amount(),
                request.collectionReference()));
        ElectronicDocument document = invoiced.document();
        if (invoiced.created() && signing.trySign(document.uuid())) {
            document = commands.document(document.uuid());
        }
        SharedPaymentView view = SharedPaymentView.from(document.invoice(), document);
        return invoiced.created()
                ? ResponseEntity.created(URI.create(InvoiceController.INVOICES + "/" + view.invoiceUuid())).body(view)
                : ResponseEntity.ok(view);
    }

    @GetMapping(AccountController.BASE_PATH + "/{admissionNumber}/shared-payments")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Copagos y cuotas esperados, facturados y pendientes por unidad facturable",
            description = "Incluye el tipo que propone la regla (cuota moderadora en contributivo ambulatorio, copago "
                    + "en lo demás) y los pagos que no se pudieron asignar a una unidad")
    OverviewView overview(@PathVariable @Pattern(regexp = "^ADM-[0-9]{4}-[0-9]{6}$") String admissionNumber) {
        return OverviewView.from(queries.overview(admissionNumber));
    }

    record Collection(@NotBlank @Pattern(regexp = "^ADM-[0-9]{4}-[0-9]{6}$") String admissionNumber,
                      String authorizationNumber, SharedPaymentKind kind, @NotNull @Positive BigDecimal amount,
                      @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{1,38}$") String collectionReference) {
    }

    record SharedPaymentView(UUID invoiceUuid, String number, String cufe, SharedPaymentKind kind,
                             String authorizationNumber, BigDecimal amount, String collectionReference,
                             Instant signedAt) {

        static SharedPaymentView from(Invoice invoice, ElectronicDocument document) {
            return new SharedPaymentView(invoice.uuid(), invoice.number(), invoice.cufe(), invoice.sharedPaymentKind(),
                    invoice.authorizationNumber(), invoice.grossTotal(), invoice.collectionReference(),
                    document == null ? null : document.signedAt());
        }
    }

    record InvoicedView(UUID invoiceUuid, String number, SharedPaymentKind kind, String authorizationNumber,
                        BigDecimal amount) {

        static InvoicedView from(Invoice invoice) {
            return new InvoicedView(invoice.uuid(), invoice.number(), invoice.sharedPaymentKind(),
                    invoice.authorizationNumber(), invoice.grossTotal());
        }
    }

    record UnitView(AccountSummary.UnitKind kind, UUID saleUuid, BigDecimal expected,
                    AccountSummary.ShareSource source, List<String> authorizations, List<InvoicedView> invoiced,
                    BigDecimal pending) {

        static UnitView from(SharedPaymentQueries.UnitShare share) {
            AccountSummary.Unit unit = share.unit();
            return new UnitView(unit.kind(), unit.saleUuid(), unit.patientShare(), unit.shareSource(),
                    unit.copayments().stream().map(Copayment::authorizationNumber).toList(),
                    share.invoiced().stream().map(InvoicedView::from).toList(), share.pending());
        }
    }

    record OverviewView(String admissionNumber, SharedPaymentKind proposedKind, List<UnitView> units,
                        List<InvoicedView> unallocated) {

        static OverviewView from(SharedPaymentQueries.Overview overview) {
            return new OverviewView(overview.summary().account().admissionNumber(), overview.proposedKind(),
                    overview.units().stream().map(UnitView::from).toList(),
                    overview.unallocated().stream().map(InvoicedView::from).toList());
        }
    }
}
