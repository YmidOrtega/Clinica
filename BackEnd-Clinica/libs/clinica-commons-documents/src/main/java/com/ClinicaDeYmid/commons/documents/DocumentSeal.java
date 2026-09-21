package com.ClinicaDeYmid.commons.documents;

import java.util.Objects;

public record DocumentSeal(String keyId, String value) {

    public DocumentSeal {
        Objects.requireNonNull(keyId, "keyId");
        Objects.requireNonNull(value, "value");
    }
}
