package com.ClinicaDeYmid.billing_service.application.dian;

import java.util.List;

public record DianReceipt(String trackId, List<String> errors) {

    public DianReceipt {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean received() {
        return trackId != null && !trackId.isBlank() && errors.isEmpty();
    }
}
