package com.ClinicaDeYmid.clinical_history_service.domain.integrity;

import java.util.List;
import java.util.UUID;

public interface LedgerEntries {

    List<LedgerEntry> recordedFor(UUID patientUuid);
}
