package com.ClinicaDeYmid.ai_assistant_service.web;

import com.ClinicaDeYmid.commons.security.AuthenticatedUser;
import com.ClinicaDeYmid.commons.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class CurrentStaff {

    private final CurrentUser currentUser;

    CurrentStaff(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    String name() {
        return currentUser.get().map(AuthenticatedUser::name).orElse("");
    }

    UUID uuid() {
        return currentUser.get().map(AuthenticatedUser::uuid)
                .orElseThrow(() -> new AccessDeniedException("El asistente atiende solo a personal de la clínica"));
    }
}
