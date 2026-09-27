package com.ClinicaDeYmid.ai_assistant_service.service;

import com.ClinicaDeYmid.ai_assistant_service.repository.FindingRepository;
import com.ClinicaDeYmid.ai_assistant_service.repository.InvoiceSnapshotRepository;
import com.ClinicaDeYmid.ai_assistant_service.repository.entity.Finding;
import com.ClinicaDeYmid.ai_assistant_service.repository.entity.InvoiceSnapshot;
import com.ClinicaDeYmid.ai_assistant_service.service.FindingViews.FindingView;
import com.ClinicaDeYmid.ai_assistant_service.service.FindingViews.RuleCount;
import com.ClinicaDeYmid.ai_assistant_service.service.FindingViews.Summary;
import com.ClinicaDeYmid.ai_assistant_service.shared.FindingRule;
import com.ClinicaDeYmid.ai_assistant_service.shared.FindingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class FindingService {

    private static final Logger log = LoggerFactory.getLogger(FindingService.class);

    private final FindingRepository findings;
    private final InvoiceSnapshotRepository invoices;
    private final Clock clock;
    private final Duration dianGrace;
    private final Duration ripsGrace;

    public FindingService(FindingRepository findings, InvoiceSnapshotRepository invoices, Clock clock,
                          @Value("${clinica.assistant.review.dian-grace:PT2H}") Duration dianGrace,
                          @Value("${clinica.assistant.review.rips-grace:PT24H}") Duration ripsGrace) {
        this.findings = findings;
        this.invoices = invoices;
        this.clock = clock;
        this.dianGrace = dianGrace;
        this.ripsGrace = ripsGrace;
    }

    @Transactional
    public void review(InvoiceSnapshot snapshot) {
        Instant now = Instant.now(clock);
        InvoiceFacts invoice = InvoiceFacts.of(snapshot);
        Map<String, Finding> open = new HashMap<>();
        findings.findByInvoiceUuidAndStatus(snapshot.invoiceUuid(), FindingStatus.OPEN)
                .forEach(finding -> open.put(key(finding.rule(), finding.subject()), finding));
        for (InvoiceReview.Expected expected : InvoiceReview.of(invoice, now, dianGrace, ripsGrace)) {
            Finding current = open.remove(key(expected.rule(), expected.subject()));
            if (current == null) {
                findings.save(Finding.open(invoice.invoiceUuid(), invoice.number(), expected.rule(),
                        expected.subject(), expected.detail(), expected.dueOn(), now));
                log.info("Finding {} opened on invoice {}", expected.rule(), invoice.number());
            } else {
                current.restate(expected.detail(), expected.dueOn());
            }
        }
        for (Finding stale : open.values()) {
            if (!stale.rule().raisedByBilling() || !InvoiceReview.stillApplies(stale, invoice)) {
                stale.resolve(now);
                log.info("Finding {} resolved on invoice {}", stale.rule(), invoice.number());
            }
        }
    }

    @Transactional
    public void raise(DeadlineAlert alert) {
        Instant now = Instant.now(clock);
        Optional<InvoiceFacts> invoice = invoices.findByInvoiceUuid(alert.invoiceUuid()).map(InvoiceFacts::of);
        String subject = alert.kind() == DeadlineAlert.Kind.OBJECTION ? alert.objectionUuid().toString()
                : Finding.WHOLE_INVOICE;
        FindingRule dueSoon = alert.kind() == DeadlineAlert.Kind.FILING ? FindingRule.FILING_DUE_SOON
                : FindingRule.OBJECTION_DUE_SOON;
        FindingRule overdue = alert.kind() == DeadlineAlert.Kind.FILING ? FindingRule.FILING_OVERDUE
                : FindingRule.OBJECTION_OVERDUE;
        FindingRule rule = alert.overdue() ? overdue : dueSoon;
        Finding probe = Finding.open(alert.invoiceUuid(), alert.invoiceNumber(), rule, subject, detailOf(alert),
                alert.deadline(), now);
        if (invoice.isPresent() && !InvoiceReview.stillApplies(probe, invoice.get())) {
            log.debug("Alert {} for invoice {} no longer applies", rule, alert.invoiceNumber());
            return;
        }
        Map<FindingRule, Finding> open = new HashMap<>();
        findings.findByInvoiceUuidAndStatus(alert.invoiceUuid(), FindingStatus.OPEN).stream()
                .filter(finding -> finding.subject().equals(subject))
                .forEach(finding -> open.put(finding.rule(), finding));
        if (!alert.overdue() && open.containsKey(overdue)) {
            return;
        }
        if (alert.overdue() && open.containsKey(dueSoon)) {
            open.remove(dueSoon).resolve(now);
        }
        Finding current = open.get(rule);
        if (current == null) {
            findings.save(probe);
            log.info("Finding {} opened on invoice {}", rule, alert.invoiceNumber());
        } else {
            current.restate(probe.detail(), probe.dueOn());
        }
    }

    @Transactional
    public int sweep() {
        List<InvoiceSnapshot> issued = invoices.findByStatus("ISSUED");
        issued.forEach(this::review);
        return issued.size();
    }

    @Transactional(readOnly = true)
    public FindingViews.PageOf<FindingView> tray(FindingStatus status, FindingRule.Severity severity,
                                                  FindingRule rule, String invoiceNumber, int page, int size) {
        var found = findings.tray(status, severity, rule, invoiceNumber, PageRequest.of(page, size));
        return new FindingViews.PageOf<>(found.map(FindingView::of).getContent(), page, size, found.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<FindingView> ofInvoice(String invoiceNumber) {
        return invoices.findByNumber(invoiceNumber)
                .map(invoice -> findings.findByInvoiceUuidOrderByDetectedAtDesc(invoice.invoiceUuid()).stream()
                        .map(FindingView::of).toList())
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        List<RuleCount> byRule = findings.openByRule().stream()
                .map(row -> new RuleCount(((FindingRule) row[0]).name(), ((FindingRule.Severity) row[1]).name(),
                        (Long) row[2]))
                .toList();
        long open = byRule.stream().mapToLong(RuleCount::open).sum();
        long high = byRule.stream().filter(count -> "HIGH".equals(count.severity())).mapToLong(RuleCount::open).sum();
        return new Summary(open, high, byRule);
    }

    private static String detailOf(DeadlineAlert alert) {
        String when = alert.overdue() ? "venció el " + alert.deadline()
                : "vence el " + alert.deadline() + " (quedan " + alert.remainingBusinessDays() + " días hábiles)";
        if (alert.kind() == DeadlineAlert.Kind.FILING) {
            return "La radicación de la factura " + alert.invoiceNumber() + " ante " + alert.payerName() + " " + when;
        }
        return "La respuesta a la " + ("DEVOLUTION".equals(alert.objectionKind()) ? "devolución " : "glosa ")
                + alert.payerRecord() + " de la factura " + alert.invoiceNumber() + " " + when
                + (alert.overdue() ? ": el pagador la puede dar por aceptada" : "");
    }

    private static String key(FindingRule rule, String subject) {
        return rule + "|" + subject;
    }
}
