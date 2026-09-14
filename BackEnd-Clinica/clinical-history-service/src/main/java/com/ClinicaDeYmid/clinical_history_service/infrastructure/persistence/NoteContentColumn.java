package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteContent;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteType;
import com.ClinicaDeYmid.clinical_history_service.domain.update.ListItemDetails;
import com.ClinicaDeYmid.clinical_history_service.domain.update.RecordUpdate;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.EncryptedContentUnreadableException;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.json.NoteContentJsonModule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

final class NoteContentColumn {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new NoteContentJsonModule())
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private NoteContentColumn() {
    }

    static byte[] write(NoteContent content) {
        try {
            return MAPPER.writerFor(NoteContent.class).writeValueAsBytes(content);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize note content of type " + content.type(), ex);
        }
    }

    static byte[] writeUpdates(List<RecordUpdate> updates) {
        try {
            return MAPPER.writerFor(new TypeReference<List<RecordUpdate>>() {
            }).writeValueAsBytes(updates);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize draft updates", ex);
        }
    }

    static List<RecordUpdate> readUpdates(byte[] json, UUID recordId) {
        try {
            return MAPPER.readValue(json, new TypeReference<List<RecordUpdate>>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Stored updates of record " + recordId + " are not readable", ex);
        }
    }

    static byte[] writeDetails(ListItemDetails details) {
        try {
            return MAPPER.writerFor(ListItemDetails.class).writeValueAsBytes(details);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize list item details", ex);
        }
    }

    static ListItemDetails readDetails(byte[] json, UUID itemId) {
        try {
            return MAPPER.readValue(json, ListItemDetails.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Stored details of list item " + itemId + " are not readable", ex);
        }
    }

    static NoteContent read(byte[] json, NoteType expectedType, UUID recordId) {
        try {
            NoteContent content = MAPPER.readValue(json, NoteContent.class);
            if (content.type() != expectedType) {
                throw new EncryptedContentUnreadableException("Content of record " + recordId + " is " + content.type()
                        + " but the record says " + expectedType);
            }
            return content;
        } catch (IOException ex) {
            throw new IllegalStateException("Stored content of record " + recordId + " is not readable", ex);
        }
    }
}
