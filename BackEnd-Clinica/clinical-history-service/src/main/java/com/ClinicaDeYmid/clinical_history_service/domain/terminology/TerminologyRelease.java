package com.ClinicaDeYmid.clinical_history_service.domain.terminology;

import java.time.Instant;
import java.util.UUID;

public record TerminologyRelease(UUID id, String version, String checksum, String sourceFile, int conceptCount, Instant importedAt,
                                 UUID importedBy, boolean active) {
}
