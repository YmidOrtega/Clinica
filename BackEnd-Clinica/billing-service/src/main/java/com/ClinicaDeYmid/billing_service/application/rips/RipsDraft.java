package com.ClinicaDeYmid.billing_service.application.rips;

import java.util.List;

public record RipsDraft(RipsDocument document, List<String> gaps) {

    public RipsDraft {
        gaps = List.copyOf(gaps);
    }

    public boolean complete() {
        return gaps.isEmpty();
    }
}
