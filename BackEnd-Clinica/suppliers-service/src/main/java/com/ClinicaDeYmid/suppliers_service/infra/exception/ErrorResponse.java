package com.ClinicaDeYmid.suppliers_service.infra.exception;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Builder
@Data
public class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String errorCode;
    private String message;
    private String userMessage;
    private List<String> errors;
    private String path;
    private String method;
    private String traceId;
    private Map<String, Object> metadata;
}

