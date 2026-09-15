package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption;
import com.ClinicaDeYmid.commons.security.RecentAuthentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(EncounterController.BASE_PATH + "/admin/encryption")
@PreAuthorize(Access.MANAGE_KEYS)
@Tag(name = "Encryption", description = "Estado y rotación de las claves que cifran el contenido clínico")
class EncryptionAdminController {

    private final ContentEncryption encryption;
    private final RecentAuthentication recentAuthentication;

    EncryptionAdminController(ContentEncryption encryption, RecentAuthentication recentAuthentication) {
        this.encryption = encryption;
        this.recentAuthentication = recentAuthentication;
    }

    record StatusView(String activeMasterKeyId, List<String> availableMasterKeyIds, long dataKeys, long pendingRewrap,
                      Map<String, Long> wrappingsPerMasterKey, boolean retiredKeysCanBeRemoved) {
        static StatusView from(ContentEncryption.Status status) {
            return new StatusView(status.activeMasterKeyId(), List.copyOf(status.availableMasterKeyIds()), status.dataKeys(),
                    status.pendingRewrap(), status.wrappingsPerMasterKey(), status.retiredKeysCanBeRemoved());
        }
    }

    record RewrapView(long rewrapped, StatusView status) {
    }

    @GetMapping
    @Operation(summary = "Consultar el estado de las claves de cifrado", description = "No expone material de claves")
    StatusView status() {
        return StatusView.from(encryption.status());
    }

    @PostMapping("/rewrap")
    @Operation(summary = "Envolver todas las claves de datos con la clave maestra activa",
            description = "Paso de la rotación: al terminar con pendingRewrap = 0 se pueden retirar las claves maestras anteriores")
    RewrapView rewrap() {
        recentAuthentication.require();
        long rewrapped = encryption.rewrapWithActiveMasterKey();
        return new RewrapView(rewrapped, StatusView.from(encryption.status()));
    }
}
