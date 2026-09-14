package com.ClinicaDeYmid.clinical_history_service.application.note;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.note.Diagnosis;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteContent;
import com.ClinicaDeYmid.clinical_history_service.domain.terminology.ConceptCatalog;
import com.ClinicaDeYmid.clinical_history_service.domain.update.RecordUpdate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClinicalCoding {

    private final ConceptCatalog catalog;

    public ClinicalCoding(ConceptCatalog catalog) {
        this.catalog = catalog;
    }

    public NoteContent resolve(NoteContent content) {
        if (content == null || content.diagnoses().isEmpty()) {
            return content;
        }
        List<Diagnosis> resolved = content.diagnoses().stream()
                .map(diagnosis -> diagnosis.resolvedAs(catalog.resolve(diagnosis.code())
                        .orElseThrow(() -> new ClinicalException.UnknownCode("diagnoses", diagnosis.code()))))
                .toList();
        return content.withDiagnoses(resolved);
    }

    public List<RecordUpdate> resolve(List<RecordUpdate> updates) {
        if (updates == null) {
            return List.of();
        }
        return updates.stream().map(update -> update instanceof RecordUpdate.AddListItem add && add.details().conditionCode() != null
                ? new RecordUpdate.AddListItem(add.details().withCondition(catalog.resolve(add.details().conditionCode())
                        .orElseThrow(() -> new ClinicalException.UnknownCode("updates.details.code", add.details().conditionCode()))))
                : update).toList();
    }
}
