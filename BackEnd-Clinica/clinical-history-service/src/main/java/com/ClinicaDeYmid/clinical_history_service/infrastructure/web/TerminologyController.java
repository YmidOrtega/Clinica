package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

import com.ClinicaDeYmid.clinical_history_service.domain.terminology.Concept;
import com.ClinicaDeYmid.clinical_history_service.domain.terminology.ConceptCatalog;
import com.ClinicaDeYmid.clinical_history_service.domain.terminology.TerminologyRelease;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.terminology.Cie10Importer;
import com.ClinicaDeYmid.commons.security.AuthenticatedUser;
import com.ClinicaDeYmid.commons.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(EncounterController.BASE_PATH)
@Tag(name = "Terminology", description = "Catálogo CIE-10 oficial (SISPRO) versionado")
class TerminologyController {

    private final ConceptCatalog catalog;
    private final Cie10Importer importer;
    private final CurrentUser currentUser;

    TerminologyController(ConceptCatalog catalog, Cie10Importer importer, CurrentUser currentUser) {
        this.catalog = catalog;
        this.importer = importer;
        this.currentUser = currentUser;
    }

    record ImportView(TerminologyRelease release, boolean created, List<String> warnings) {
    }

    @GetMapping("/terminology/cie10")
    @PreAuthorize(Access.CLINICAL_STAFF)
    @Operation(summary = "Buscar diagnósticos CIE-10 por prefijo de código o por texto")
    List<Concept> search(@RequestParam("q") @Size(min = 2, max = 60, message = "debe tener entre 2 y 60 caracteres") String query,
                         @RequestParam(defaultValue = "20") int limit) {
        return catalog.search(query, Math.clamp(limit, 1, 50));
    }

    @GetMapping("/admin/terminology/cie10/releases")
    @PreAuthorize(Access.MANAGE_CATALOGS)
    @Operation(summary = "Listar las versiones importadas del catálogo CIE-10")
    List<TerminologyRelease> releases() {
        return importer.releases();
    }

    @PostMapping(path = "/admin/terminology/cie10/releases", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Access.MANAGE_CATALOGS)
    @Operation(summary = "Importar la Tabla CIE-10 de SISPRO (.xlsx, hoja 'Final')",
            description = "Idempotente por checksum; no activa la versión, eso es un paso aparte")
    ResponseEntity<ImportView> importRelease(@RequestPart("file") MultipartFile file) throws IOException {
        Cie10Importer.ImportResult result = importer.importWorkbook(file.getOriginalFilename(), file.getBytes(), actor());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new ImportView(result.release(), result.created(), result.warnings()));
    }

    @PostMapping("/admin/terminology/cie10/releases/{id}/activation")
    @PreAuthorize(Access.MANAGE_CATALOGS)
    @Operation(summary = "Activar una versión del catálogo CIE-10 para nuevas notas")
    TerminologyRelease activate(@PathVariable UUID id) {
        return importer.activate(id, actor());
    }

    private UUID actor() {
        return currentUser.get().map(AuthenticatedUser::uuid).orElseThrow();
    }
}
