package com.ClinicaDeYmid.ai_assistant_service.service;

import com.ClinicaDeYmid.ai_assistant_service.repository.entity.Finding;
import com.ClinicaDeYmid.ai_assistant_service.shared.FindingRule;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class InvoiceReview {

    record Expected(FindingRule rule, String subject, String detail, LocalDate dueOn) {

        Expected(FindingRule rule, String detail) {
            this(rule, Finding.WHOLE_INVOICE, detail, null);
        }
    }

    private InvoiceReview() {
    }

    static List<Expected> of(InvoiceFacts invoice, Instant now, Duration dianGrace, Duration ripsGrace) {
        List<Expected> expected = new ArrayList<>();
        if (invoice.voided()) {
            return expected;
        }
        if ("REJECTED".equals(invoice.dianStatus())) {
            expected.add(new Expected(FindingRule.DIAN_REJECTED, "La DIAN rechazó la factura " + invoice.number()
                    + ": hay que corregirla y reenviarla, o anularla con nota crédito"));
        } else if (!"ACCEPTED".equals(invoice.dianStatus())
                && invoice.lastEventAt().plus(dianGrace).isBefore(now)) {
            expected.add(new Expected(FindingRule.DIAN_UNCONFIRMED, "La factura " + invoice.number()
                    + (invoice.dianStatus() == null ? " sigue sin firmar o sin enviar a la DIAN"
                    : " sigue esperando la validación de la DIAN") + " después de " + hours(dianGrace)));
        }
        invoice.creditNotes().stream().filter(note -> "REJECTED".equals(note.dianStatus())).forEach(note ->
                expected.add(new Expected(FindingRule.CREDIT_NOTE_REJECTED, note.uuid(), "La DIAN rechazó la nota crédito "
                        + note.number() + " de la factura " + invoice.number(), null)));
        if (invoice.servicesToAPayer() && "ACCEPTED".equals(invoice.dianStatus()) && invoice.cuv() == null
                && invoice.dianStatusAt() != null && invoice.dianStatusAt().plus(ripsGrace).isBefore(now)) {
            expected.add(new Expected(FindingRule.RIPS_PENDING, "La factura " + invoice.number()
                    + " fue aceptada por la DIAN pero su RIPS no tiene CUV del Ministerio: sin CUV no se puede radicar"));
        }
        if (invoice.shareShortfall() != null && invoice.shareShortfall().signum() > 0) {
            expected.add(new Expected(FindingRule.COPAY_SHORTFALL, "Al paciente de la factura " + invoice.number()
                    + " le faltó facturar " + invoice.shareShortfall().toPlainString()
                    + " de copago o cuota: el pagador puede glosarlo"));
        }
        if (invoice.uncontractedCare() != null) {
            expected.add(new Expected(FindingRule.BILLED_WITHOUT_CONTRACT, "La factura " + invoice.number()
                    + " se emitió al pagador sin contrato (" + invoice.uncontractedCare()
                    + "): conviene tener a mano los soportes del caso"));
        }
        return expected;
    }

    static boolean stillApplies(Finding finding, InvoiceFacts invoice) {
        if (invoice.voided()) {
            return false;
        }
        return switch (finding.rule()) {
            case FILING_DUE_SOON, FILING_OVERDUE -> !invoice.filed();
            case OBJECTION_DUE_SOON, OBJECTION_OVERDUE -> invoice.awaitingAnswer(finding.subject());
            default -> throw new IllegalArgumentException(finding.rule() + " is not raised by billing");
        };
    }

    private static String hours(Duration grace) {
        long hours = grace.toHours();
        return hours == 1 ? "1 hora" : hours + " horas";
    }
}
