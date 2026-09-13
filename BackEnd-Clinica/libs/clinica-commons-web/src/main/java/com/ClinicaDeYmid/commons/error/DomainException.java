package com.ClinicaDeYmid.commons.error;

import java.util.Objects;

public abstract class DomainException extends RuntimeException {

    private final ErrorCategory category;
    private final String code;

    protected DomainException(ErrorCategory category, String code, String publicMessage) {
        this(category, code, publicMessage, null);
    }

    protected DomainException(ErrorCategory category, String code, String publicMessage, Throwable cause) {
        super(publicMessage, cause);
        this.category = Objects.requireNonNull(category, "category");
        this.code = Objects.requireNonNull(code, "code");
    }

    public ErrorCategory category() {
        return category;
    }

    public String code() {
        return code;
    }
}
