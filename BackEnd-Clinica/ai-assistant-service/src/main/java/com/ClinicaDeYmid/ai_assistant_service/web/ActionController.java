package com.ClinicaDeYmid.ai_assistant_service.web;

import com.ClinicaDeYmid.ai_assistant_service.service.ActionService;
import com.ClinicaDeYmid.ai_assistant_service.service.ActionViews;
import com.ClinicaDeYmid.ai_assistant_service.shared.ActionStatus;
import com.ClinicaDeYmid.ai_assistant_service.web.ActionResponses.ActionView;
import com.ClinicaDeYmid.commons.web.EntityTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ActionController.BASE_PATH)
@Tag(name = "Acciones", description = "Acciones que propone el asistente y que solo ocurren si el usuario las confirma")
class ActionController {

    static final String BASE_PATH = "/api/v1/assistant/actions";

    private final ActionService actions;
    private final CurrentStaff staff;

    ActionController(ActionService actions, CurrentStaff staff) {
        this.actions = actions;
        this.staff = staff;
    }

    @GetMapping
    @PreAuthorize(Access.USE)
    @Operation(summary = "Mis acciones propuestas, de la más reciente a la más antigua")
    List<ActionView> mine(@RequestParam(required = false) ActionStatus status) {
        return actions.mine(staff.uuid(), status).stream().map(ActionView::from).toList();
    }

    @PostMapping("/{uuid}/confirmation")
    @PreAuthorize(Access.USE)
    @Operation(summary = "Confirmar una acción propuesta",
            description = "La ejecuta en billing con los permisos del usuario. Exige el ETag de la propuesta; una "
                    + "vencida, ya confirmada o descartada no se ejecuta")
    ResponseEntity<ActionView> confirm(@PathVariable UUID uuid,
                                       @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(actions.confirm(staff.uuid(), uuid, EntityTags.requiredVersion(ifMatch)));
    }

    @PostMapping("/{uuid}/discard")
    @PreAuthorize(Access.USE)
    @Operation(summary = "Descartar una acción propuesta")
    ResponseEntity<ActionView> discard(@PathVariable UUID uuid,
                                       @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(actions.discard(staff.uuid(), uuid, EntityTags.requiredVersion(ifMatch)));
    }

    private static ResponseEntity<ActionView> respond(ActionViews.ActionView action) {
        return ResponseEntity.ok().eTag(EntityTags.of(action.version())).body(ActionView.from(action));
    }
}
