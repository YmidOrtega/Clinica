package com.ClinicaDeYmid.patient_service.infra.exception.base;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Respuesta estándar de error")
public class ErrorResponse {

    @Schema(description = "Timestamp del error", example = "2024-01-15T10:30:00Z")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private ZonedDateTime timestamp;

    @Schema(description = "Código de estado HTTP", example = "404")
    private int status;

    @Schema(description = "Nombre del error HTTP", example = "Not Found")
    private String error;

    @Schema(description = "Código de error específico de la aplicación", example = "RESOURCE_NOT_FOUND")
    private String errorCode;

    @Schema(description = "Mensaje descriptivo del error")
    private String message;

    @Schema(description = "Mensaje para el usuario final")
    private String userMessage;

    @Schema(description = "Ruta de la petición", example = "/api/v1/patients/123")
    private String path;

    @Schema(description = "Método HTTP", example = "GET")
    private String method;

    @Schema(description = "Operación que falló", example = "GET_PATIENT")
    private String operation;

    @Schema(description = "Lista de errores de validación")
    private List<ValidationError> validationErrors;

    @Schema(description = "Metadatos adicionales del error")
    private Map<String, Object> metadata;

    @Schema(description = "ID de traza para debugging")
    private String traceId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationError {
        @Schema(description = "Campo que falló la validación", example = "email")
        private String field;

        @Schema(description = "Valor rechazado")
        private Object rejectedValue;

        @Schema(description = "Mensaje de error de validación")
        private String message;

        @Schema(description = "Código de error de validación", example = "INVALID_EMAIL")
        private String code;
    }
}