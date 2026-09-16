package com.ClinicaDeYmid.patient_service.application;

import java.util.UUID;

public record Payer(UUID uuid, String nit, String name, String type) {
}
