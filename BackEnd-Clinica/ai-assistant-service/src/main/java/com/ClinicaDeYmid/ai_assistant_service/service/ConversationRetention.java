package com.ClinicaDeYmid.ai_assistant_service.service;

import com.ClinicaDeYmid.ai_assistant_service.repository.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class ConversationRetention {

    private static final Logger log = LoggerFactory.getLogger(ConversationRetention.class);

    private final ConversationRepository conversations;
    private final Clock clock;
    private final Duration retention;

    ConversationRetention(ConversationRepository conversations, Clock clock,
                          @Value("${clinica.assistant.conversations.retention:P30D}") Duration retention) {
        this.conversations = conversations;
        this.clock = clock;
        this.retention = retention;
    }

    @Transactional
    @Scheduled(cron = "${clinica.assistant.conversations.purge-cron:0 30 3 * * *}",
            zone = "${clinica.assistant.time-zone:America/Bogota}")
    public int purge() {
        int purged = conversations.purgeUntouchedSince(Instant.now(clock).minus(retention));
        log.info("Purged {} conversations untouched for {}", purged, retention);
        return purged;
    }
}
