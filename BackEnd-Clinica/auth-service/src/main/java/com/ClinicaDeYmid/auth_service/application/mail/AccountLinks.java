package com.ClinicaDeYmid.auth_service.application.mail;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public record AccountLinks(String activationUrl, String passwordResetUrl) {

    public String linkFor(MailKind kind, String token) {
        String base = switch (kind) {
            case ACTIVATION -> activationUrl;
            case PASSWORD_RESET -> passwordResetUrl;
        };
        return base + (base.contains("?") ? "&" : "?") + "token=" + URLEncoder.encode(token, StandardCharsets.US_ASCII);
    }
}
