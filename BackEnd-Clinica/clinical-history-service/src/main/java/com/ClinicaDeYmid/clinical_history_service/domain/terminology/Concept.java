package com.ClinicaDeYmid.clinical_history_service.domain.terminology;

import java.util.Objects;

public record Concept(String code, String display, String categoryCode, String categoryDisplay, int chapter, String chapterDisplay) {

    public Concept {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(categoryCode, "categoryCode");
        Objects.requireNonNull(chapterDisplay, "chapterDisplay");
    }
}
