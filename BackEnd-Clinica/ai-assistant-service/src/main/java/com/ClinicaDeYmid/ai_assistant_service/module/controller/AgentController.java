package com.ClinicaDeYmid.ai_assistant_service.module.controller;

import com.ClinicaDeYmid.ai_assistant_service.module.dto.ChatRequestDto;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.ChatResponseDto;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.ConversationHistoryDto;
import com.ClinicaDeYmid.ai_assistant_service.module.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/ai-assistant")
@RequiredArgsConstructor
@Tag(name = "AI Assistant", description = "Endpoints para interacción con el asistente virtual IA")
@SecurityRequirement(name = "bearerAuth")
public class AgentController {

    private final ChatService chatService;

    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RECEPTIONIST')")
    @Operation(
            summary = "Enviar mensaje al asistente IA",
            description = "Procesa un mensaje del usuario y genera una respuesta utilizando Gemini AI. " +
                    "Mantiene el historial de conversación y detecta intenciones para ejecutar acciones."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Respuesta generada exitosamente",
                    content = @Content(schema = @Schema(implementation = ChatResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Solicitud inválida - mensaje vacío o excede límite de caracteres"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autenticado - token JWT inválido o ausente"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Acceso denegado - rol insuficiente"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor"
            )
    })
    public ResponseEntity<ChatResponseDto> chat(
            @Valid @RequestBody ChatRequestDto request
    ) {
        log.info("Received chat request with message length: {}", request.message().length());
        ChatResponseDto response = chatService.processMessage(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RECEPTIONIST')")
    @Operation(
            summary = "Obtener historial de conversaciones",
            description = "Recupera todas las conversaciones del usuario autenticado, incluyendo mensajes y metadata."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Historial recuperado exitosamente",
                    content = @Content(schema = @Schema(implementation = ConversationHistoryDto.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autenticado"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Acceso denegado"
            )
    })
    public ResponseEntity<List<ConversationHistoryDto>> getHistory() {
        log.info("Fetching conversation history for authenticated user");
        List<ConversationHistoryDto> history = chatService.getUserHistory();
        return ResponseEntity.ok(history);
    }

    @DeleteMapping("/conversation/{sessionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RECEPTIONIST')")
    @Operation(
            summary = "Cerrar conversación",
            description = "Cierra una conversación activa por su session ID. " +
                    "La conversación se marca como inactiva pero se mantiene en el historial."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Conversación cerrada exitosamente"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autenticado"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Acceso denegado"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Conversación no encontrada"
            )
    })
    public ResponseEntity<Void> closeConversation(
            @Parameter(description = "ID de sesión de la conversación", required = true)
            @PathVariable String sessionId
    ) {
        log.info("Closing conversation with sessionId: {}", sessionId);
        chatService.closeConversation(sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/health")
    @Operation(
            summary = "Health check del servicio AI",
            description = "Verifica que el servicio de IA está operativo. No requiere autenticación."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Servicio operativo"
    )
    public ResponseEntity<HealthResponse> health() {
        log.debug("Health check requested");
        return ResponseEntity.ok(new HealthResponse("AI Assistant Service is running", "UP"));
    }

    // DTO interno para health check
    private record HealthResponse(String message, String status) {}
}