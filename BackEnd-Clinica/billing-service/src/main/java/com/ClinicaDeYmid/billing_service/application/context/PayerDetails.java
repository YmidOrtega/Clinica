package com.ClinicaDeYmid.billing_service.application.context;

import java.util.UUID;

public record PayerDetails(UUID uuid, String name, String nit, String type) {
}
