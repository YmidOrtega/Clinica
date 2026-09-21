package com.ClinicaDeYmid.admissions_service.domain;

import java.time.Instant;

public sealed interface Discharge {

    enum Code {
        MEDICAL,
        VOLUNTARY,
        REFERRAL,
        ESCAPE,
        DEATH
    }

    Instant at();

    record Medical(Instant at, String notes) implements Discharge {
        public Medical {
            DomainRules.required(at, "at");
            notes = DomainRules.optionalText(notes, "notes", 500);
        }
    }

    record Voluntary(Instant at, String signedBy, String signatureDocument) implements Discharge {
        public Voluntary {
            DomainRules.required(at, "at");
            signedBy = DomainRules.requiredText(signedBy, "signedBy", 200);
            signatureDocument = DomainRules.requiredText(signatureDocument, "signatureDocument", 30);
        }
    }

    record Referral(Instant at, String repsCode, String facility, String reason) implements Discharge {
        public Referral {
            DomainRules.required(at, "at");
            repsCode = DomainRules.requiredDigits(repsCode, "repsCode", 10, 14);
            facility = DomainRules.requiredText(facility, "facility", 200);
            reason = DomainRules.requiredText(reason, "reason", 500);
        }
    }

    record Escape(Instant at, Instant noticedAt) implements Discharge {
        public Escape {
            DomainRules.required(at, "at");
            DomainRules.required(noticedAt, "noticedAt");
            if (noticedAt.isAfter(at)) {
                throw new AdmissionsException.InvalidData("noticedAt", "no puede ser posterior al egreso");
            }
        }
    }

    record Death(Instant at, Instant occurredAt, String certificateNumber) implements Discharge {
        public Death {
            DomainRules.required(at, "at");
            DomainRules.required(occurredAt, "occurredAt");
            if (occurredAt.isAfter(at)) {
                throw new AdmissionsException.InvalidData("occurredAt", "no puede ser posterior al egreso");
            }
            certificateNumber = DomainRules.requiredText(certificateNumber, "certificateNumber", 60);
        }
    }

    static Discharge of(Code type, Instant at, String notes, String signedBy, String signatureDocument,
                        String repsCode, String facility, String reason, Instant noticedAt, Instant occurredAt,
                        String certificateNumber) {
        return switch (DomainRules.required(type, "type")) {
            case MEDICAL -> new Medical(at, notes);
            case VOLUNTARY -> new Voluntary(at, signedBy, signatureDocument);
            case REFERRAL -> new Referral(at, repsCode, facility, reason);
            case ESCAPE -> new Escape(at, noticedAt);
            case DEATH -> new Death(at, occurredAt, certificateNumber);
        };
    }

    default Code code() {
        return switch (this) {
            case Medical ignored -> Code.MEDICAL;
            case Voluntary ignored -> Code.VOLUNTARY;
            case Referral ignored -> Code.REFERRAL;
            case Escape ignored -> Code.ESCAPE;
            case Death ignored -> Code.DEATH;
        };
    }
}
