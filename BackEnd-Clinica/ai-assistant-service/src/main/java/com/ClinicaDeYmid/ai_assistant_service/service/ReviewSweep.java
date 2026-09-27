package com.ClinicaDeYmid.ai_assistant_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "clinica.assistant.review.sweep-enabled", havingValue = "true", matchIfMissing = true)
class ReviewSweep {

    private static final Logger log = LoggerFactory.getLogger(ReviewSweep.class);

    private final FindingService findings;

    ReviewSweep(FindingService findings) {
        this.findings = findings;
    }

    @Scheduled(fixedDelayString = "${clinica.assistant.review.sweep-delay:PT30M}",
            initialDelayString = "${clinica.assistant.review.sweep-delay:PT30M}")
    void reviewTheTimeRules() {
        int reviewed = findings.sweep();
        log.debug("Swept {} issued invoices for time rules", reviewed);
    }
}
