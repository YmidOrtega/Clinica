package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteContent;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.json.NoteContentJsonModule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

final class NoteContentColumn {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new NoteContentJsonModule())
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private NoteContentColumn() {
    }

    static String write(NoteContent content) {
        try {
            return MAPPER.writerFor(NoteContent.class).writeValueAsString(content);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize note content of type " + content.type(), ex);
        }
    }

    static NoteContent read(String json) {
        try {
            return MAPPER.readValue(json, NoteContent.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored note content is not readable", ex);
        }
    }
}
