package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

import com.ClinicaDeYmid.clinical_history_service.application.integrity.IntegrityQueries;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity.EcdsaClinicalSignature;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.web.IntegrityResponses.NoteSignatureView;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.web.IntegrityResponses.PatientIntegrityView;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.web.IntegrityResponses.SealKeyView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(EncounterController.BASE_PATH)
@Tag(name = "Integrity", description = "Firma, sello institucional y verificación de la cadena de la historia clínica")
class IntegrityController {

    private final IntegrityQueries queries;
    private final EcdsaClinicalSignature signature;
    private final Clock clock;

    IntegrityController(IntegrityQueries queries, EcdsaClinicalSignature signature, Clock clock) {
        this.queries = queries;
        this.signature = signature;
        this.clock = clock;
    }

    @GetMapping("/patients/{patientUuid}/integrity")
    @PreAuthorize(Access.VERIFY_INTEGRITY)
    @Operation(summary = "Verificar la cadena sellada de la historia de un paciente",
            description = "Recalcula hashes y sellos de cada atención, nota, anulación y cierre; no devuelve contenido clínico")
    PatientIntegrityView verifyPatient(@PathVariable UUID patientUuid) {
        return PatientIntegrityView.from(patientUuid, queries.verifyPatient(patientUuid), Instant.now(clock));
    }

    @GetMapping("/notes/{id}/signature")
    @PreAuthorize(Access.CLINICAL_STAFF)
    @Operation(summary = "Consultar y verificar la firma y el sello de una nota")
    NoteSignatureView noteSignature(@PathVariable UUID id) {
        return NoteSignatureView.from(queries.noteSignature(id), signature.algorithm());
    }

    @GetMapping("/seal-keys")
    @Operation(summary = "Claves públicas del sello institucional", description = "Públicas para que terceros verifiquen copias de la historia")
    List<SealKeyView> sealKeys() {
        return signature.publicKeys().entrySet().stream()
                .map(key -> new SealKeyView(key.getKey(), signature.algorithm(), "P-256", key.getValue(),
                        key.getKey().equals(signature.activeKeyId())))
                .toList();
    }
}
