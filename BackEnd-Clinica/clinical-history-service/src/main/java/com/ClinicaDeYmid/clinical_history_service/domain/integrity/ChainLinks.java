package com.ClinicaDeYmid.clinical_history_service.domain.integrity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChainLinks {

    Optional<ChainLink> lockHead(UUID patientUuid);

    void append(ChainLink link);

    List<ChainLink> chainOf(UUID patientUuid);

    Optional<ChainLink> linkOf(LedgerEntry.Key entry);
}
