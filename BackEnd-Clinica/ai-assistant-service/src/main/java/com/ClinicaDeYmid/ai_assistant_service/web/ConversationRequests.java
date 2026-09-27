package com.ClinicaDeYmid.ai_assistant_service.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class ConversationRequests {

    private ConversationRequests() {
    }

    record Start(@NotBlank @Size(max = 120) String title) {
    }
}
