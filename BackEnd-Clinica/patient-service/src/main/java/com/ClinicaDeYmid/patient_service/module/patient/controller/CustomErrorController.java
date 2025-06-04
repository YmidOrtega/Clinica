package com.ClinicaDeYmid.patient_service.module.patient.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest; // Importar ServletWebRequest
import org.springframework.web.context.request.WebRequest; // Importar WebRequest

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/error")
public class CustomErrorController implements ErrorController {

    @Autowired
    private ErrorAttributes errorAttributes;

    @GetMapping
    public ResponseEntity<Map<String, Object>> handleError(HttpServletRequest request) {
        // Envolver HttpServletRequest en ServletWebRequest
        WebRequest webRequest = new ServletWebRequest(request);

        Map<String, Object> attributes = errorAttributes.getErrorAttributes(
                webRequest, // Usar webRequest aquí
                ErrorAttributeOptions.of(ErrorAttributeOptions.Include.MESSAGE)
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", attributes.getOrDefault("status", 500));
        response.put("error", attributes.getOrDefault("error", "Error"));
        response.put("message", attributes.getOrDefault("message", "Error inesperado"));
        response.put("path", attributes.getOrDefault("path", request.getRequestURI()));

        return ResponseEntity.status((int) attributes.getOrDefault("status", 500)).body(response);
    }
}