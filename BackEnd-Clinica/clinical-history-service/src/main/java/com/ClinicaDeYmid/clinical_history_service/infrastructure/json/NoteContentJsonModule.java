package com.ClinicaDeYmid.clinical_history_service.infrastructure.json;

import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteContent;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.util.List;

public class NoteContentJsonModule extends SimpleModule {

    public NoteContentJsonModule() {
        super("clinical-note-content");
        setMixInAnnotation(NoteContent.class, NoteContentMixin.class);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = NoteContent.Admission.class, name = "ADMISSION"),
            @JsonSubTypes.Type(value = NoteContent.Progress.class, name = "PROGRESS"),
            @JsonSubTypes.Type(value = NoteContent.Triage.class, name = "TRIAGE"),
            @JsonSubTypes.Type(value = NoteContent.Consultation.class, name = "CONSULTATION"),
            @JsonSubTypes.Type(value = NoteContent.Nursing.class, name = "NURSING"),
            @JsonSubTypes.Type(value = NoteContent.Discharge.class, name = "DISCHARGE"),
            @JsonSubTypes.Type(value = NoteContent.Addendum.class, name = "ADDENDUM")
    })
    @JsonIgnoreProperties(ignoreUnknown = false)
    interface NoteContentMixin {

        @JsonIgnore
        NoteType type();

        @JsonIgnore
        List<String> missingFields();
    }
}
