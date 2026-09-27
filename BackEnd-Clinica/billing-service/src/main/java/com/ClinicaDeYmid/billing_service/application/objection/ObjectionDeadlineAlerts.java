package com.ClinicaDeYmid.billing_service.application.objection;

import com.ClinicaDeYmid.billing_service.domain.BusinessDeadline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class ObjectionDeadlineAlerts {

    static final int SCAN_LIMIT = 5000;

    private static final Logger log = LoggerFactory.getLogger(ObjectionDeadlineAlerts.class);

    private final PayerObjectionService objections;
    private final ObjectionAlertOutbox outbox;
    private final TransactionOperations transactions;

    public ObjectionDeadlineAlerts(PayerObjectionService objections, ObjectionAlertOutbox outbox,
                                   TransactionOperations transactions) {
        this.objections = objections;
        this.outbox = outbox;
        this.transactions = transactions;
    }

    public int run() {
        int published = 0;
        for (ObjectionStatus status : objections.tray(null, null, SCAN_LIMIT)) {
            if (status.responseDue().state() == BusinessDeadline.State.ON_TIME) {
                continue;
            }
            try {
                Boolean alerted = transactions.execute(tx -> outbox.alertOnce(status.objection(), status.responseDue()));
                if (Boolean.TRUE.equals(alerted)) {
                    published++;
                }
            } catch (DataIntegrityViolationException alreadyAlerted) {
                log.debug("Objection {} was already alerted as {}", status.objection().uuid(),
                        status.responseDue().state());
            }
        }
        if (published > 0) {
            log.info("Published {} objection response deadline alerts", published);
        }
        return published;
    }
}
