package com.ClinicaDeYmid.clinical_history_service.domain.update;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ListItemHistory(UUID itemId, UUID patientUuid, ListItemDetails details, List<Event> events) {

    public record Event(UUID id, ListItemStatus status, String reason, NoteOrigin origin) {
    }

    public ListItemHistory {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(patientUuid, "patientUuid");
        Objects.requireNonNull(details, "details");
        events = events.stream().sorted(Comparator.comparing(event -> event.origin().recordedAt())).toList();
        if (events.isEmpty()) {
            throw new IllegalArgumentException("A list item needs at least the event that added it");
        }
    }

    public ListCategory category() {
        return details.category();
    }

    public NoteOrigin addedBy() {
        return events.getFirst().origin();
    }

    public ListItemStatus status() {
        if (addedBy().voided()) {
            return ListItemStatus.ENTERED_IN_ERROR;
        }
        return events.stream().filter(event -> !event.origin().voided()).reduce((first, second) -> second)
                .map(Event::status).orElse(ListItemStatus.ENTERED_IN_ERROR);
    }

    public ListItemState state() {
        return new ListItemState(itemId, patientUuid, category(), status());
    }
}
