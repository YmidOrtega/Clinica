package com.ClinicaDeYmid.clinical_history_service.domain.note;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteDrafts {

    void add(NoteDraft draft);

    Optional<NoteDraft> find(UUID id);

    Optional<NoteDraft> lock(UUID id);

    boolean replace(NoteDraft revised, long expectedVersion);

    void remove(UUID id);

    List<NoteDraft> writtenBy(UUID authorUuid);
}
