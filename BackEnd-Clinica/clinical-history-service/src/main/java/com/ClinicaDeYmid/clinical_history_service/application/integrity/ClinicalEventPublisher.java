package com.ClinicaDeYmid.clinical_history_service.application.integrity;

import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainLink;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.LedgerEntry;

public interface ClinicalEventPublisher {

    void publish(LedgerEntry entry, ChainLink link);
}
