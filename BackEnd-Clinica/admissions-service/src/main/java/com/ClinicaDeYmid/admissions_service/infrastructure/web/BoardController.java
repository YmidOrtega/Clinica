package com.ClinicaDeYmid.admissions_service.infrastructure.web;

import com.ClinicaDeYmid.admissions_service.application.AdmissionQueries;
import com.ClinicaDeYmid.admissions_service.application.BedQueries;
import com.ClinicaDeYmid.admissions_service.infrastructure.web.AdmissionResponses.AdmissionSummaryView;
import com.ClinicaDeYmid.admissions_service.infrastructure.web.AdmissionResponses.CensusEntryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(BoardController.BASE_PATH)
@Tag(name = "Tableros", description = "Censo de camas y cola de atención de un servicio")
class BoardController {

    static final String BASE_PATH = "/api/v1/admissions";

    private final BedQueries beds;
    private final AdmissionQueries admissions;

    BoardController(BedQueries beds, AdmissionQueries admissions) {
        this.beds = beds;
        this.admissions = admissions;
    }

    @GetMapping("/locations/{uuid}/census")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Censo de una ubicación: cada cama con su estado y, si está ocupada, de quién",
            description = "Devuelve todas las camas instaladas en la ubicación, ordenadas por habitación")
    List<CensusEntryView> census(@PathVariable UUID uuid) {
        return beds.censusOf(uuid).stream().map(CensusEntryView::from).toList();
    }

    @GetMapping("/configured-services/{uuid}/queue")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Cola de un servicio configurado: episodios abiertos en él, por orden de llegada",
            description = "Incluye los registrados que todavía no se activan; el orden pasará a regirse por "
                    + "el nivel de triage cuando clinical-history lo publique")
    List<AdmissionSummaryView> queue(@PathVariable UUID uuid) {
        return admissions.queueOf(uuid).stream().map(AdmissionSummaryView::from).toList();
    }
}
