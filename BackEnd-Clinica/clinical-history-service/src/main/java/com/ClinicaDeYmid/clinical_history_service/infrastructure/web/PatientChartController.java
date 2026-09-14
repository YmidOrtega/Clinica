package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

import com.ClinicaDeYmid.clinical_history_service.application.chart.PatientChartQueries;
import com.ClinicaDeYmid.clinical_history_service.domain.update.ListCategory;
import com.ClinicaDeYmid.clinical_history_service.domain.update.ListItemDetails;
import com.ClinicaDeYmid.clinical_history_service.domain.update.ListItemHistory;
import com.ClinicaDeYmid.clinical_history_service.domain.update.NoteOrigin;
import com.ClinicaDeYmid.clinical_history_service.domain.update.VitalSignKind;
import com.ClinicaDeYmid.clinical_history_service.domain.update.VitalSignObservation;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.web.ClinicalResponses.ClinicianView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(EncounterController.BASE_PATH + "/patients/{patientUuid}")
@PreAuthorize(Access.CLINICAL_STAFF)
@Tag(name = "Patient chart", description = "Listas vivas y signos vitales derivados de notas firmadas")
class PatientChartController {

    private final PatientChartQueries queries;
    private final CurrentClinician clinician;

    PatientChartController(PatientChartQueries queries, CurrentClinician clinician) {
        this.queries = queries;
        this.clinician = clinician;
    }

    record OriginView(UUID noteId, UUID encounterId, ClinicianView author, Instant recordedAt, boolean voided, String restriction) {
        static OriginView from(NoteOrigin origin) {
            return new OriginView(origin.noteId(), origin.encounterId(), ClinicianView.from(origin.author()), origin.recordedAt(),
                    origin.voided(), ClinicalResponses.restrictionOf(origin.restriction()));
        }
    }

    record ListEventView(UUID id, String status, String reason, OriginView origin) {
    }

    record ListItemView(UUID itemId, UUID patientUuid, String category, String status, ListItemDetails details, List<ListEventView> history) {
        static ListItemView from(ListItemHistory item) {
            return new ListItemView(item.itemId(), item.patientUuid(), item.category().name(), item.status().name(), item.details(),
                    item.events().stream().map(event -> new ListEventView(event.id(), event.status().name(), event.reason(),
                            OriginView.from(event.origin()))).toList());
        }
    }

    record ListsView(List<ListItemView> items, int restrictedItemsHidden) {
    }

    record VitalSignView(UUID id, String kind, BigDecimal value, String unit, Instant measuredAt, OriginView origin) {
        static VitalSignView from(VitalSignObservation observation) {
            return new VitalSignView(observation.id(), observation.kind().name(), observation.value(), observation.kind().unit(),
                    observation.measuredAt(), OriginView.from(observation.origin()));
        }
    }

    record VitalSignsView(List<VitalSignView> observations, int restrictedObservationsHidden) {
    }

    @GetMapping("/lists")
    @Operation(summary = "Consultar alergias, enfermedades crónicas, medicamentos y antecedentes del paciente",
            description = "Cada ítem muestra su estado vigente y el historial de notas firmadas que lo originaron o cambiaron")
    ListsView lists(@PathVariable UUID patientUuid, @RequestParam(required = false) ListCategory category) {
        PatientChartQueries.Visible<ListItemHistory> visible = queries.listItems(patientUuid, category, clinician.require());
        return new ListsView(visible.items().stream().map(ListItemView::from).toList(), visible.restrictedHidden());
    }

    @GetMapping("/vital-signs")
    @Operation(summary = "Consultar las series de signos vitales del paciente")
    VitalSignsView vitalSigns(@PathVariable UUID patientUuid, @RequestParam(required = false) VitalSignKind kind,
                              @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        PatientChartQueries.Visible<VitalSignObservation> visible = queries.vitalSigns(patientUuid, kind, from, to, clinician.require());
        return new VitalSignsView(visible.items().stream().map(VitalSignView::from).toList(), visible.restrictedHidden());
    }
}
