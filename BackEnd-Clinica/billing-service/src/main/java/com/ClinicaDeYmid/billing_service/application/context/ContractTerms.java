package com.ClinicaDeYmid.billing_service.application.context;

import java.util.UUID;

public record ContractTerms(UUID uuid, String number, String modality, String coveragePlanCode, String cucon) {
}
