package com.ClinicaDeYmid.billing_service.application.filing;

import com.ClinicaDeYmid.billing_service.domain.FilingDeadline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class FilingDeadlineAlerts {

    static final int SCAN_LIMIT = 5000;

    private static final Logger log = LoggerFactory.getLogger(FilingDeadlineAlerts.class);

    private final InvoiceFilingService filings;
    private final FilingAlertOutbox outbox;
    private final TransactionOperations transactions;

    public FilingDeadlineAlerts(InvoiceFilingService filings, FilingAlertOutbox outbox,
                                TransactionOperations transactions) {
        this.filings = filings;
        this.outbox = outbox;
        this.transactions = transactions;
    }

    public int run() {
        int published = 0;
        for (FilingStatus status : filings.tray(null, null, SCAN_LIMIT)) {
            if (status.deadline().state() == FilingDeadline.State.ON_TIME) {
                continue;
            }
            try {
                Boolean alerted = transactions.execute(tx -> outbox.alertOnce(status.invoice(), status.deadline(),
                        status.cuv()));
                if (Boolean.TRUE.equals(alerted)) {
                    published++;
                }
            } catch (DataIntegrityViolationException alreadyAlerted) {
                log.debug("Invoice {} was already alerted as {}", status.invoice().number(), status.deadline().state());
            }
        }
        if (published > 0) {
            log.info("Published {} filing deadline alerts", published);
        }
        return published;
    }
}
