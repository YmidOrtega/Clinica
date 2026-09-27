package com.ClinicaDeYmid.ai_assistant_service.client;

import com.ClinicaDeYmid.ai_assistant_service.shared.ActionKind;

import java.util.UUID;

public record BillingAction(ActionKind kind, UUID invoiceUuid, String body, UUID targetUuid, Long targetVersion) {
}
