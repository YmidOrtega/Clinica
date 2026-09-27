package com.ClinicaDeYmid.ai_assistant_service.service;

import com.ClinicaDeYmid.ai_assistant_service.client.BillingDirectory;
import com.ClinicaDeYmid.ai_assistant_service.client.BillingDirectory.Aspect;
import com.ClinicaDeYmid.ai_assistant_service.client.BillingLookup;
import com.ClinicaDeYmid.ai_assistant_service.repository.InvoiceSnapshotRepository;
import com.ClinicaDeYmid.ai_assistant_service.repository.entity.InvoiceSnapshot;
import com.ClinicaDeYmid.ai_assistant_service.shared.FindingRule;
import com.ClinicaDeYmid.ai_assistant_service.shared.FindingStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class InvoiceTools {

    static final int MAX_RESULT_LENGTH = 6000;
    static final int MAX_INVOICES = 20;

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private final InvoiceSnapshotRepository invoices;
    private final FindingService findings;
    private final BillingDirectory billing;
    private final Clock clock;

    public InvoiceTools(InvoiceSnapshotRepository invoices, FindingService findings, BillingDirectory billing,
                        Clock clock) {
        this.invoices = invoices;
        this.findings = findings;
        this.billing = billing;
        this.clock = clock;
    }

    @Tool(description = "Hallazgos abiertos de la bandeja de revisión de facturas, de los más graves y urgentes a los "
            + "menos. Sirve para responder qué facturas tienen problemas.")
    public String openFindings(@ToolParam(required = false, description = "HIGH, MEDIUM o LOW") String severity,
                               @ToolParam(required = false, description = "Código de la regla, por ejemplo DIAN_REJECTED "
                                       + "o FILING_OVERDUE") String rule) {
        return write(findings.tray(FindingStatus.OPEN, parse(FindingRule.Severity.class, severity),
                parse(FindingRule.class, rule), null, 0, MAX_INVOICES));
    }

    @Tool(description = "Estado resumido de una factura según la copia local: DIAN, CUV, radicado, saldo y sus hallazgos.")
    @Transactional(readOnly = true)
    public String invoiceStatus(@ToolParam(description = "Número de la factura, por ejemplo SETP990000001") String number) {
        return snapshot(number).map(invoice -> {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("number", invoice.number());
            status.put("status", invoice.status());
            status.put("issuedOn", invoice.issuedOn());
            status.put("admissionNumber", invoice.admissionNumber());
            status.put("buyer", invoice.buyerKind());
            status.put("payerNit", invoice.payerNit());
            status.put("contractNumber", invoice.contractNumber());
            status.put("uncontractedCare", invoice.uncontractedCare());
            status.put("payableTotal", invoice.payableTotal());
            status.put("creditedTotal", invoice.creditedTotal());
            status.put("balance", invoice.balance());
            status.put("dianStatus", invoice.dianStatus());
            status.put("cuv", invoice.cuv() != null);
            status.put("filingNumber", invoice.filingNumber());
            status.put("filedOn", invoice.filedOn());
            status.put("lastChange", invoice.lastEventType());
            status.put("findings", findings.ofInvoice(invoice.number()));
            return write(status);
        }).orElseGet(() -> unknown(number));
    }

    @Tool(description = "Busca facturas emitidas por NIT del pagador, estado ante la DIAN y rango de fechas de emisión. "
            + "Devuelve como máximo 20, de la más reciente a la más antigua.")
    @Transactional(readOnly = true)
    public String searchInvoices(@ToolParam(required = false, description = "NIT del pagador sin dígito de verificación") String payerNit,
                                 @ToolParam(required = false, description = "ACCEPTED, REJECTED o AWAITING_VALIDATION") String dianStatus,
                                 @ToolParam(required = false, description = "Desde, AAAA-MM-DD; por defecto hace 30 días") String from,
                                 @ToolParam(required = false, description = "Hasta, AAAA-MM-DD; por defecto hoy") String to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate since = blank(from) ? today.minusDays(30) : LocalDate.parse(from.strip());
        LocalDate until = blank(to) ? today : LocalDate.parse(to.strip());
        List<Map<String, Object>> found = invoices.search(blank(payerNit) ? null : payerNit.strip(), null,
                        blank(dianStatus) ? null : dianStatus.strip().toUpperCase(Locale.ROOT), since, until,
                        PageRequest.of(0, MAX_INVOICES)).stream()
                .map(invoice -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("number", invoice.number());
                    row.put("issuedOn", invoice.issuedOn());
                    row.put("payerNit", invoice.payerNit());
                    row.put("balance", invoice.balance());
                    row.put("dianStatus", invoice.dianStatus());
                    row.put("filed", invoice.filingNumber() != null);
                    return row;
                }).toList();
        return write(found);
    }

    @Tool(description = "Factura completa desde billing, con sus líneas, totales y copagos. Requiere permiso de lectura en facturación.")
    public String invoiceDetail(@ToolParam(description = "Número de la factura") String number) {
        return fromBilling(number, Aspect.INVOICE);
    }

    @Tool(description = "Respuestas de la DIAN a los envíos de una factura: por qué la rechazó o si la aceptó.")
    public String dianVerdicts(@ToolParam(description = "Número de la factura") String number) {
        return fromBilling(number, Aspect.DIAN_VERDICTS);
    }

    @Tool(description = "Envíos del RIPS de una factura al Ministerio (MUV) y sus hallazgos de validación.")
    public String ripsValidations(@ToolParam(description = "Número de la factura") String number) {
        return fromBilling(number, Aspect.RIPS_VALIDATIONS);
    }

    @Tool(description = "Radicado de la factura ante el pagador, o su plazo y semáforo si aún no se ha radicado.")
    public String filing(@ToolParam(description = "Número de la factura") String number) {
        return fromBilling(number, Aspect.FILING);
    }

    @Tool(description = "Devoluciones y glosas del pagador sobre una factura, con sus causales, respuestas y decisión.")
    public String objections(@ToolParam(description = "Número de la factura") String number) {
        return fromBilling(number, Aspect.OBJECTIONS);
    }

    @Tool(description = "Notas crédito de una factura y su estado ante la DIAN.")
    public String creditNotes(@ToolParam(description = "Número de la factura") String number) {
        return fromBilling(number, Aspect.CREDIT_NOTES);
    }

    private String fromBilling(String number, Aspect aspect) {
        Optional<InvoiceSnapshot> invoice = snapshot(number);
        if (invoice.isEmpty()) {
            return unknown(number);
        }
        return switch (billing.read(aspect, invoice.get().invoiceUuid())) {
            case BillingLookup.Found found -> limit(found.json());
            case BillingLookup.NotFound ignored -> "{\"error\":\"billing no encontró la factura " + number + "\"}";
            case BillingLookup.Forbidden ignored ->
                    "{\"error\":\"el usuario no tiene permiso en billing para consultar esto\"}";
            case BillingLookup.Unavailable ignored ->
                    "{\"error\":\"billing no respondió; la información no está disponible ahora\"}";
            case BillingLookup.Refused refused -> "{\"error\":\"billing rechazó la consulta (" + refused.status() + ")\"}";
        };
    }

    private Optional<InvoiceSnapshot> snapshot(String number) {
        return blank(number) ? Optional.empty() : invoices.findByNumber(number.strip().toUpperCase(Locale.ROOT));
    }

    private static String unknown(String number) {
        return "{\"error\":\"no hay una factura emitida con número " + number + "\"}";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (blank(value)) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static String write(Object value) {
        try {
            return limit(JSON.writeValueAsString(value));
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Cannot write the tool result", impossible);
        }
    }

    static String limit(String json) {
        return json.length() <= MAX_RESULT_LENGTH ? json
                : json.substring(0, MAX_RESULT_LENGTH) + "…[recortado: pida un dato más específico]";
    }
}
