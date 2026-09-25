package com.ClinicaDeYmid.billing_service.domain;

import java.util.Optional;

public enum SurgicalComponent {

    SURGEON(SurgicalRole.SURGEON),
    ANESTHESIOLOGIST(SurgicalRole.ANESTHESIOLOGIST),
    ASSISTANT(SurgicalRole.ASSISTANT),
    OPERATING_ROOM(null),
    MATERIALS(null);

    private final SurgicalRole performedBy;

    SurgicalComponent(SurgicalRole performedBy) {
        this.performedBy = performedBy;
    }

    public Optional<SurgicalRole> performedBy() {
        return Optional.ofNullable(performedBy);
    }
}
