package com.ClinicaDeYmid.clinical_history_service.application.attachment;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;

public record AttachmentRetention(Period afterLastCare) {

    public Instant retainUntil(Instant lastCare) {
        return lastCare.atOffset(ZoneOffset.UTC).plus(afterLastCare).plusDays(1).toInstant();
    }
}
