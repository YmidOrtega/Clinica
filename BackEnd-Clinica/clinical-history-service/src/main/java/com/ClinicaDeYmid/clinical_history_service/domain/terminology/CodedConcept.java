package com.ClinicaDeYmid.clinical_history_service.domain.terminology;

import java.util.Objects;

public record CodedConcept(String code, String display, String catalogVersion) {

    public CodedConcept {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(catalogVersion, "catalogVersion");
    }
}
