package com.ClinicaDeYmid.clinical_history_service.domain.update;

import java.util.UUID;

public record ListItemState(UUID itemId, UUID patientUuid, ListCategory category, ListItemStatus status) {
}
