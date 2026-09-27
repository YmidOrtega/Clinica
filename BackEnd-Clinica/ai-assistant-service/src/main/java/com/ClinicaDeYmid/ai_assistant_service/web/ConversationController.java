package com.ClinicaDeYmid.ai_assistant_service.web;

import com.ClinicaDeYmid.ai_assistant_service.service.ConversationService;
import com.ClinicaDeYmid.ai_assistant_service.service.ConversationViews;
import com.ClinicaDeYmid.ai_assistant_service.web.ConversationResponses.ConversationView;
import com.ClinicaDeYmid.ai_assistant_service.web.ConversationResponses.DetailView;
import com.ClinicaDeYmid.ai_assistant_service.web.ConversationResponses.PageView;
import com.ClinicaDeYmid.commons.web.EntityTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@Validated
@RequestMapping(ConversationController.BASE_PATH)
@Tag(name = "Conversaciones", description = "Conversaciones del usuario con el asistente de revisión de facturas")
class ConversationController {

    static final String BASE_PATH = "/api/v1/assistant/conversations";

    private final ConversationService conversations;
    private final CurrentStaff staff;

    ConversationController(ConversationService conversations, CurrentStaff staff) {
        this.conversations = conversations;
        this.staff = staff;
    }

    @PostMapping
    @PreAuthorize(Access.USE)
    @Operation(summary = "Abrir una conversación con el asistente")
    ResponseEntity<ConversationView> start(@Valid @RequestBody ConversationRequests.Start request) {
        ConversationViews.ConversationSummary started = conversations.start(staff.uuid(), request.title());
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + started.uuid()))
                .eTag(EntityTags.of(started.version()))
                .body(ConversationView.from(started));
    }

    @GetMapping
    @PreAuthorize(Access.USE)
    @Operation(summary = "Mis conversaciones, de la más reciente a la más antigua",
            description = "Cada usuario ve solo las suyas")
    PageView mine(@RequestParam(defaultValue = "0") @Min(0) int page,
                  @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return PageView.from(conversations.mine(staff.uuid(), page, size));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize(Access.USE)
    @Operation(summary = "Una conversación con sus mensajes", description = "La de otro usuario responde 404")
    ResponseEntity<DetailView> get(@PathVariable UUID uuid) {
        ConversationViews.ConversationDetail detail = conversations.get(staff.uuid(), uuid);
        return ResponseEntity.ok().eTag(EntityTags.of(detail.summary().version())).body(DetailView.from(detail));
    }

    @PostMapping("/{uuid}/closure")
    @PreAuthorize(Access.USE)
    @Operation(summary = "Cerrar una conversación", description = "Exige el ETag de la conversación")
    ResponseEntity<ConversationView> close(@PathVariable UUID uuid,
                                           @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        ConversationViews.ConversationSummary closed = conversations.close(staff.uuid(), uuid,
                EntityTags.requiredVersion(ifMatch));
        return ResponseEntity.ok().eTag(EntityTags.of(closed.version())).body(ConversationView.from(closed));
    }
}
