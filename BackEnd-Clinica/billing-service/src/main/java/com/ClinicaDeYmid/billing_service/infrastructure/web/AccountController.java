package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.application.AccountQueries;
import com.ClinicaDeYmid.billing_service.domain.AccountStatus;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;
import com.ClinicaDeYmid.billing_service.infrastructure.web.AccountResponses.AccountView;
import com.ClinicaDeYmid.commons.web.EntityTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping(AccountController.BASE_PATH)
@Tag(name = "Cuentas", description = "Cuenta de cada episodio, abierta y cerrada por los eventos de admisiones")
class AccountController {

    static final String BASE_PATH = "/api/v1/billing/accounts";

    private final AccountQueries queries;

    AccountController(AccountQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/{admissionNumber}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar la cuenta de un episodio por su número de atención")
    ResponseEntity<AccountView> account(
            @PathVariable @Pattern(regexp = "^ADM-[0-9]{4}-[0-9]{6}$") String admissionNumber) {
        EpisodeAccount account = queries.byAdmissionNumber(admissionNumber);
        return ResponseEntity.ok().eTag(EntityTags.of(account.version())).body(AccountView.from(account));
    }

    @GetMapping
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar cuentas por estado",
            description = "FROZEN son los episodios egresados que esperan factura, del más antiguo al más reciente")
    List<AccountView> accounts(@RequestParam AccountStatus.Code status,
                               @RequestParam(defaultValue = "50") int limit) {
        return queries.inStatus(status, limit).stream().map(AccountView::from).toList();
    }
}
