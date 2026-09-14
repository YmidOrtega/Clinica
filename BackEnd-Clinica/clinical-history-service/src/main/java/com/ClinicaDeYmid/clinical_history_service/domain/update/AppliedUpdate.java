package com.ClinicaDeYmid.clinical_history_service.domain.update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public sealed interface AppliedUpdate {

    UUID id();

    record ListItemAdded(UUID id, ListItemDetails details) implements AppliedUpdate {

        public ListItemAdded {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(details, "details");
        }
    }

    record ListItemStatusChanged(UUID id, UUID itemId, ListCategory category, ListItemStatus status, String reason)
            implements AppliedUpdate {

        public ListItemStatusChanged {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(itemId, "itemId");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(reason, "reason");
        }
    }

    record VitalSignObserved(UUID id, VitalSignKind kind, BigDecimal value, Instant measuredAt) implements AppliedUpdate {

        public VitalSignObserved {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(measuredAt, "measuredAt");
        }
    }
}
