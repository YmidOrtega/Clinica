package com.ClinicaDeYmid.admissions_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.Instant;

@Embeddable
public class DischargeDetails {

    @Enumerated(EnumType.STRING)
    @Column(name = "discharge_type", length = 20)
    private Discharge.Code type;

    @Column(name = "discharge_notes", length = 500)
    private String notes;

    @Column(name = "discharge_signed_by", length = 200)
    private String signedBy;

    @Column(name = "discharge_signature_document", length = 30)
    private String signatureDocument;

    @Column(name = "referral_reps_code", length = 20)
    private String repsCode;

    @Column(name = "referral_facility", length = 200)
    private String facility;

    @Column(name = "referral_reason", length = 500)
    private String reason;

    @Column(name = "escape_noticed_at")
    private Instant noticedAt;

    @Column(name = "death_occurred_at")
    private Instant occurredAt;

    @Column(name = "death_certificate_number", length = 60)
    private String certificateNumber;

    protected DischargeDetails() {
    }

    static DischargeDetails of(Discharge discharge) {
        DischargeDetails details = new DischargeDetails();
        details.type = discharge.code();
        switch (discharge) {
            case Discharge.Medical medical -> details.notes = medical.notes();
            case Discharge.Voluntary voluntary -> {
                details.signedBy = voluntary.signedBy();
                details.signatureDocument = voluntary.signatureDocument();
            }
            case Discharge.Referral referral -> {
                details.repsCode = referral.repsCode();
                details.facility = referral.facility();
                details.reason = referral.reason();
            }
            case Discharge.Escape escape -> details.noticedAt = escape.noticedAt();
            case Discharge.Death death -> {
                details.occurredAt = death.occurredAt();
                details.certificateNumber = death.certificateNumber();
            }
        }
        return details;
    }

    Discharge toDischarge(Instant at) {
        return Discharge.of(type, at, notes, signedBy, signatureDocument, repsCode, facility, reason, noticedAt,
                occurredAt, certificateNumber);
    }
}
