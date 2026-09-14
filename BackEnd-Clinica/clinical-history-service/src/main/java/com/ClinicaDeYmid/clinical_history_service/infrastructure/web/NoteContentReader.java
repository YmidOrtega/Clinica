package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteContent;
import com.ClinicaDeYmid.commons.error.DomainException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.stream.Collectors;

@Component
class NoteContentReader {

    private final ObjectReader reader;

    NoteContentReader(ObjectMapper mapper) {
        this.reader = mapper.readerFor(NoteContent.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }

    NoteContent read(JsonNode content) {
        if (content == null || content.isNull()) {
            throw new ClinicalException.InvalidData("content", "es obligatorio");
        }
        try {
            return reader.readValue(content);
        } catch (InvalidTypeIdException unknownType) {
            throw new ClinicalException.InvalidData("content.type", "debe ser uno de ADMISSION, PROGRESS, TRIAGE, CONSULTATION, NURSING, DISCHARGE, ADDENDUM");
        } catch (UnrecognizedPropertyException unknownField) {
            throw new ClinicalException.InvalidData("content." + unknownField.getPropertyName(), "no pertenece a este tipo de nota");
        } catch (JsonMappingException invalid) {
            if (invalid.getCause() instanceof DomainException domainProblem) {
                throw domainProblem;
            }
            throw new ClinicalException.InvalidData(pathOf(invalid), "tiene un formato inválido");
        } catch (IOException unreadable) {
            throw new ClinicalException.InvalidData("content", "tiene un formato inválido");
        }
    }

    private static String pathOf(JsonMappingException problem) {
        String path = problem.getPath().stream()
                .map(reference -> reference.getFieldName() != null ? reference.getFieldName() : String.valueOf(reference.getIndex()))
                .collect(Collectors.joining("."));
        return path.isEmpty() ? "content" : "content." + path;
    }
}
