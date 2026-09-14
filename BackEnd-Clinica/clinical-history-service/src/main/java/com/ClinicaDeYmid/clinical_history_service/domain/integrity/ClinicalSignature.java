package com.ClinicaDeYmid.clinical_history_service.domain.integrity;

import java.time.Instant;
import java.util.Set;

public interface ClinicalSignature {

    ChainLink seal(LedgerEntry entry, ChainLink previous, Instant sealedAt);

    Set<IntegrityProblem.Kind> check(LedgerEntry entry, ChainLink link);
}
