package com.ClinicaDeYmid.clinical_history_service.application.chart;

import com.ClinicaDeYmid.clinical_history_service.application.access.RecordAccess;
import com.ClinicaDeYmid.clinical_history_service.application.patient.PatientDirectory;
import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessAction;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.update.ListCategory;
import com.ClinicaDeYmid.clinical_history_service.domain.update.ListItemHistory;
import com.ClinicaDeYmid.clinical_history_service.domain.update.NoteOrigin;
import com.ClinicaDeYmid.clinical_history_service.domain.update.PatientChart;
import com.ClinicaDeYmid.clinical_history_service.domain.update.VitalSignKind;
import com.ClinicaDeYmid.clinical_history_service.domain.update.VitalSignObservation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PatientChartQueries {

    private final PatientDirectory patients;
    private final PatientChart chart;
    private final RecordAccess access;

    public PatientChartQueries(PatientDirectory patients, PatientChart chart, RecordAccess access) {
        this.patients = patients;
        this.chart = chart;
        this.access = access;
    }

    public record Visible<T>(List<T> items, int restrictedHidden) {
    }

    public Visible<ListItemHistory> listItems(UUID patientUuid, ListCategory category, Clinician reader) {
        RecordAccess.Visibility visibility = access.patientVisibility(reader, patientUuid, AccessAction.READ_LISTS);
        List<ListItemHistory> all = chart.listItemsOf(patients.samePersonSubjects(patientUuid)).stream()
                .filter(item -> category == null || item.category() == category)
                .sorted(Comparator.comparing((ListItemHistory item) -> item.category()).thenComparing(item -> item.addedBy().recordedAt()))
                .toList();
        List<ListItemHistory> visible = all.stream().filter(item -> isVisible(item.addedBy(), visibility)).toList();
        access.recordRead(reader, patientUuid, AccessAction.READ_LISTS, visibility, visible.stream().anyMatch(item -> item.addedBy().isRestricted()));
        return new Visible<>(visible, all.size() - visible.size());
    }

    public Visible<VitalSignObservation> vitalSigns(UUID patientUuid, VitalSignKind kind, Instant from, Instant to, Clinician reader) {
        RecordAccess.Visibility visibility = access.patientVisibility(reader, patientUuid, AccessAction.READ_VITAL_SIGNS);
        List<VitalSignObservation> all = chart.vitalSignsOf(patients.samePersonSubjects(patientUuid), kind, from, to).stream()
                .filter(observation -> !observation.origin().voided())
                .toList();
        List<VitalSignObservation> visible = all.stream().filter(observation -> isVisible(observation.origin(), visibility)).toList();
        access.recordRead(reader, patientUuid, AccessAction.READ_VITAL_SIGNS, visibility,
                visible.stream().anyMatch(observation -> observation.origin().isRestricted()));
        return new Visible<>(visible, all.size() - visible.size());
    }

    private static boolean isVisible(NoteOrigin origin, RecordAccess.Visibility visibility) {
        return !origin.isRestricted() || visibility.canSeeRestricted(origin.author().uuid(), origin.encounterId());
    }
}
