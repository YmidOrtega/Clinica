package com.ClinicaDeYmid.admissions_service.application.coverage;

import java.util.UUID;

public sealed interface CoverageVerdict {

    record Covered(UUID contractUuid, String contractNumber, UUID payerUuid) implements CoverageVerdict {
    }

    record NotCovered(String reason, UUID payerUuid) implements CoverageVerdict {
    }

    record Unknown(String reason, UUID payerUuid) implements CoverageVerdict {
    }
}
