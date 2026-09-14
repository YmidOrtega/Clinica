package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteContent;
import com.ClinicaDeYmid.clinical_history_service.domain.update.ListItemDetails;
import com.ClinicaDeYmid.clinical_history_service.domain.update.RecordUpdate;
import com.ClinicaDeYmid.commons.error.DomainException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
class NoteContentReader {

    private final ObjectReader reader;
    private final ObjectReader updatesReader;

    NoteContentReader(ObjectMapper mapper) {
        this.reader = mapper.readerFor(NoteContent.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        this.updatesReader = mapper.readerFor(new TypeReference<List<RecordUpdate>>() {
                })
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }

    List<RecordUpdate> readUpdates(JsonNode updates) {
        if (updates == null || updates.isNull()) {
            return List.of();
        }
        if (!updates.isArray()) {
            throw new ClinicalException.InvalidData("updates", "debe ser una lista");
        }
        return parse(updatesReader, updates, "updates");
    }

    NoteContent read(JsonNode content) {
        if (content == null || content.isNull()) {
            throw new ClinicalException.InvalidData("content", "es obligatorio");
        }
        return parse(reader, content, "content");
    }

    private static <T> T parse(ObjectReader objectReader, JsonNode node, String root) {
        try {
            return objectReader.readValue(node);
        } catch (InvalidTypeIdException unknownType) {
            throw new ClinicalException.InvalidData(pathOf(unknownType, root) + "." + discriminatorOf(unknownType),
                    "tiene un valor desconocido o ausente");
        } catch (UnrecognizedPropertyException unknownField) {
            throw new ClinicalException.InvalidData(root + "." + unknownField.getPropertyName(), "no pertenece a este tipo de nota");
        } catch (JsonMappingException invalid) {
            if (invalid.getCause() instanceof DomainException domainProblem) {
                throw domainProblem;
            }
            throw new ClinicalException.InvalidData(pathOf(invalid, root), "tiene un formato inválido");
        } catch (IOException unreadable) {
            throw new ClinicalException.InvalidData(root, "tiene un formato inválido");
        }
    }

    private static String discriminatorOf(InvalidTypeIdException problem) {
        Class<?> base = problem.getBaseType() == null ? Object.class : problem.getBaseType().getRawClass();
        if (RecordUpdate.class.isAssignableFrom(base)) {
            return "kind";
        }
        return ListItemDetails.class.isAssignableFrom(base) ? "category" : "type";
    }

    private static String pathOf(JsonMappingException problem, String root) {
        String path = problem.getPath().stream()
                .map(reference -> reference.getFieldName() != null ? reference.getFieldName() : String.valueOf(reference.getIndex()))
                .collect(Collectors.joining("."));
        return path.isEmpty() ? root : root + "." + path;
    }
}
