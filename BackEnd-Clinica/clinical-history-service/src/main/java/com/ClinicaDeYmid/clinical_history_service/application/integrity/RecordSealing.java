package com.ClinicaDeYmid.clinical_history_service.application.integrity;

import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainLink;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainLinks;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ClinicalSignature;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.LedgerEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class RecordSealing {

    private final ChainLinks links;
    private final ClinicalSignature signature;
    private final ClinicalEventPublisher events;
    private final Clock clock;

    public RecordSealing(ChainLinks links, ClinicalSignature signature, ClinicalEventPublisher events, Clock clock) {
        this.links = links;
        this.signature = signature;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockRecordOf(UUID patientUuid) {
        links.lockHead(patientUuid);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ChainLink record(LedgerEntry entry) {
        ChainLink previous = links.lockHead(entry.patientUuid()).orElse(null);
        ChainLink link = signature.seal(entry, previous, Instant.now(clock));
        links.append(link);
        events.publish(entry, link);
        return link;
    }
}
