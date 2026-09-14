package com.ClinicaDeYmid.clinical_history_service.infrastructure.terminology;

import com.ClinicaDeYmid.commons.error.DomainException;
import com.ClinicaDeYmid.commons.error.ErrorCategory;

public class InvalidWorkbookException extends DomainException {

    InvalidWorkbookException(String message) {
        super(ErrorCategory.INVALID_INPUT, "INVALID_CIE10_FILE", message);
    }
}
