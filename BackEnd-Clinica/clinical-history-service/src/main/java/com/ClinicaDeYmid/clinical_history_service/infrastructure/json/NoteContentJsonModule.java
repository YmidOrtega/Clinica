package com.ClinicaDeYmid.clinical_history_service.infrastructure.json;

import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteContent;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteType;
import com.ClinicaDeYmid.clinical_history_service.domain.update.AppliedUpdate;
import com.ClinicaDeYmid.clinical_history_service.domain.update.ListCategory;
import com.ClinicaDeYmid.clinical_history_service.domain.update.ListItemDetails;
import com.ClinicaDeYmid.clinical_history_service.domain.update.RecordUpdate;
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
        setMixInAnnotation(RecordUpdate.class, RecordUpdateMixin.class);
        setMixInAnnotation(ListItemDetails.class, ListItemDetailsMixin.class);
        setMixInAnnotation(AppliedUpdate.class, AppliedUpdateMixin.class);
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

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = RecordUpdate.AddListItem.class, name = "ADD_LIST_ITEM"),
            @JsonSubTypes.Type(value = RecordUpdate.ChangeListItemStatus.class, name = "CHANGE_LIST_ITEM_STATUS"),
            @JsonSubTypes.Type(value = RecordUpdate.RecordVitalSigns.class, name = "RECORD_VITAL_SIGNS")
    })
    @JsonIgnoreProperties(ignoreUnknown = false)
    interface RecordUpdateMixin {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "category")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = ListItemDetails.Allergy.class, name = "ALLERGY"),
            @JsonSubTypes.Type(value = ListItemDetails.ChronicCondition.class, name = "CHRONIC_CONDITION"),
            @JsonSubTypes.Type(value = ListItemDetails.CurrentMedication.class, name = "CURRENT_MEDICATION"),
            @JsonSubTypes.Type(value = ListItemDetails.FamilyHistory.class, name = "FAMILY_HISTORY"),
            @JsonSubTypes.Type(value = ListItemDetails.PastHistory.class, name = "PAST_HISTORY"),
            @JsonSubTypes.Type(value = ListItemDetails.Vaccination.class, name = "VACCINATION")
    })
    @JsonIgnoreProperties(ignoreUnknown = false)
    interface ListItemDetailsMixin {

        @JsonIgnore
        ListCategory category();

        @JsonIgnore
        String conditionCode();
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = AppliedUpdate.ListItemAdded.class, name = "LIST_ITEM_ADDED"),
            @JsonSubTypes.Type(value = AppliedUpdate.ListItemStatusChanged.class, name = "LIST_ITEM_STATUS_CHANGED"),
            @JsonSubTypes.Type(value = AppliedUpdate.VitalSignObserved.class, name = "VITAL_SIGN_OBSERVED")
    })
    interface AppliedUpdateMixin {
    }

}
