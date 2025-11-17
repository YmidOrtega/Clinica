package com.ClinicaDeYmid.patient_service.infra.exception;

import com.ClinicaDeYmid.patient_service.infra.exception.base.BaseException;
import lombok.Getter;

@Getter
public class BusinessException extends BaseException {

    private final String businessRule;

    public BusinessException(String message) {
        super(message, "BUSINESS_RULE_VIOLATION", "BUSINESS_VALIDATION");
        this.businessRule = null;
    }

    public BusinessException(String message, String businessRule) {
        super(message, "BUSINESS_RULE_VIOLATION", "BUSINESS_VALIDATION");
        this.businessRule = businessRule;
    }

    public BusinessException(String message, String businessRule, Throwable cause) {
        super(message, "BUSINESS_RULE_VIOLATION", "BUSINESS_VALIDATION", cause);
        this.businessRule = businessRule;
    }
}