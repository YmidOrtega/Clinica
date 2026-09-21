package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.domain.AdmissionsException;
import com.ClinicaDeYmid.admissions_service.domain.Discharge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DischargeTest {

    private static final Instant NOW = Instant.parse("2026-09-21T14:00:00Z");

    @Test
    void aMedicalDischargeNeedsNothingBeyondTheMoment() {
        Discharge discharge = new Discharge.Medical(NOW, null);

        assertThat(discharge.code()).isEqualTo(Discharge.Code.MEDICAL);
        assertThat(discharge.at()).isEqualTo(NOW);
    }

    @Test
    void aVoluntaryDischargeDemandsWhoSignsTheResponsibility() {
        assertThatThrownBy(() -> new Discharge.Voluntary(NOW, " ", "1094921345"))
                .isInstanceOf(AdmissionsException.InvalidData.class);
        assertThatThrownBy(() -> new Discharge.Voluntary(NOW, "Carlos Restrepo", null))
                .isInstanceOf(AdmissionsException.InvalidData.class);

        Discharge.Voluntary signed = new Discharge.Voluntary(NOW, "Carlos  Restrepo", "1094921345");
        assertThat(signed.signedBy()).isEqualTo("Carlos Restrepo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"05001", "05001000010A", "  "})
    void aReferralDemandsARepsCodeOfDigits(String code) {
        assertThatThrownBy(() -> new Discharge.Referral(NOW, code, "Hospital San Vicente", "Requiere neurocirugía"))
                .isInstanceOf(AdmissionsException.InvalidData.class);
    }

    @Test
    void aReferralKeepsWhereAndWhyThePatientLeaves() {
        Discharge.Referral referral =
                new Discharge.Referral(NOW, "050010000101", "Hospital San Vicente", "Requiere neurocirugía");

        assertThat(referral.repsCode()).isEqualTo("050010000101");
        assertThat(referral.facility()).isEqualTo("Hospital San Vicente");
        assertThat(referral.code()).isEqualTo(Discharge.Code.REFERRAL);
    }

    @Test
    void anEscapeCannotBeNoticedAfterItIsRecorded() {
        assertThatThrownBy(() -> new Discharge.Escape(NOW, NOW.plusSeconds(60)))
                .isInstanceOf(AdmissionsException.InvalidData.class);

        assertThat(new Discharge.Escape(NOW, NOW.minusSeconds(1800)).noticedAt())
                .isEqualTo(NOW.minusSeconds(1800));
    }

    @Test
    void aDeathDemandsItsCertificateAndCannotHappenLater() {
        assertThatThrownBy(() -> new Discharge.Death(NOW, NOW.plusSeconds(60), "CD-2026-0001"))
                .isInstanceOf(AdmissionsException.InvalidData.class);
        assertThatThrownBy(() -> new Discharge.Death(NOW, NOW, null))
                .isInstanceOf(AdmissionsException.InvalidData.class);

        assertThat(new Discharge.Death(NOW, NOW.minusSeconds(600), "CD-2026-0001").code())
                .isEqualTo(Discharge.Code.DEATH);
    }

    @Test
    void theTypeDecidesWhichDischargeIsBuilt() {
        Discharge escape = Discharge.of(Discharge.Code.ESCAPE, NOW, null, null, null, null, null, null,
                NOW.minusSeconds(120), null, null);

        assertThat(escape).isInstanceOf(Discharge.Escape.class);
        assertThatThrownBy(() -> Discharge.of(null, NOW, null, null, null, null, null, null, null, null, null))
                .isInstanceOf(AdmissionsException.InvalidData.class);
    }
}
