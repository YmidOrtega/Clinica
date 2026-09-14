package com.ClinicaDeYmid.patient_service.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

final class PatientEventJson {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private PatientEventJson() {
    }

    static String write(PatientEventMessage message) {
        try {
            return MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize patient event " + message.type(), ex);
        }
    }
}
