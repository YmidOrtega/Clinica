package com.ClinicaDeYmid.ai_assistant_service.module.service;

import com.ClinicaDeYmid.ai_assistant_service.module.dto.AttentionExtractionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttentionDataExtractor {

    private static final Pattern ACTION_PATTERN =
            Pattern.compile("##ACTION##\\s*(\\{.*?\\})\\s*##END_ACTION##", Pattern.DOTALL);

    private static final Set<String> VALID_CAUSES = Set.of(
            "ILLNESS", "ACCIDENT", "WORK_ACCIDENT", "TRAFFIC_ACCIDENT",
            "VIOLENCE", "MATERNITY", "PREVENTION", "CONTROL",
            "EMERGENCY", "ROUTINE_CHECKUP", "VACCINATION", "OTHER"
    );

    private static final Set<String> VALID_TRIAGE_LEVELS = Set.of(
            "RED", "ORANGE", "YELLOW", "GREEN", "BLUE"
    );

    private final ObjectMapper objectMapper;

    public Optional<AttentionExtractionResult> extract(String aiResponse) {
        Matcher matcher = ACTION_PATTERN.matcher(aiResponse);
        if (!matcher.find()) {
            return Optional.empty();
        }

        try {
            // Normalize patient_id: LLMs often emit it as a bare number; coerce to string
            String json = matcher.group(1)
                    .replaceAll("(\"patient_id\"\\s*:\\s*)(\\d+)", "$1\"$2\"");
            AttentionExtractionResult result = objectMapper.readValue(
                    json, AttentionExtractionResult.class
            );
            validate(result);
            return Optional.of(result);
        } catch (IllegalArgumentException e) {
            log.warn("AI action block failed validation: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to parse action JSON from AI response: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public String stripActionBlock(String aiResponse) {
        return ACTION_PATTERN.matcher(aiResponse).replaceAll("").strip();
    }

    private void validate(AttentionExtractionResult r) {
        if (r.patientId() == null || r.patientId().isBlank())
            throw new IllegalArgumentException("patient_id is required");
        if (r.doctorId() == null || r.doctorId() <= 0)
            throw new IllegalArgumentException("doctor_id must be a positive number");
        if (r.configurationServiceId() == null || r.configurationServiceId() <= 0)
            throw new IllegalArgumentException("configuration_service_id must be a positive number");
        if (r.cause() == null || !VALID_CAUSES.contains(r.cause().toUpperCase()))
            throw new IllegalArgumentException("Invalid cause: " + r.cause());
        if (r.healthProviders() == null || r.healthProviders().isEmpty())
            throw new IllegalArgumentException("health_providers is required");

        r.healthProviders().forEach(hp -> {
            if (hp.nit() == null || hp.nit().isBlank())
                throw new IllegalArgumentException("health_provider nit is required");
            if (hp.contractId() == null || hp.contractId() <= 0)
                throw new IllegalArgumentException("health_provider contract_id must be a positive number");
        });

        if (r.triageLevel() != null && !r.triageLevel().isBlank()
                && !VALID_TRIAGE_LEVELS.contains(r.triageLevel().toUpperCase())) {
            throw new IllegalArgumentException("Invalid triage_level: " + r.triageLevel());
        }
    }
}
