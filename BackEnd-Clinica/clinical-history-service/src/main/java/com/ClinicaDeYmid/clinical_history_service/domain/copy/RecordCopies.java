package com.ClinicaDeYmid.clinical_history_service.domain.copy;

import java.util.Optional;
import java.util.UUID;

public interface RecordCopies {

    void add(RecordCopy copy);

    Optional<RecordCopy> find(UUID id);
}
