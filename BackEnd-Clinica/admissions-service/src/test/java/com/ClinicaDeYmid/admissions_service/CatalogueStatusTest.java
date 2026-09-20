package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionsException;
import com.ClinicaDeYmid.admissions_service.domain.CatalogueStatus;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.ServiceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogueStatusTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-20T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void aNewEntryStartsActiveAndUsable() {
        Location location = Location.define("Piso 3");

        assertThat(location.status()).isInstanceOf(CatalogueStatus.Active.class);
        assertThat(location.status().usable()).isTrue();
    }

    @Test
    void retiringKeepsTheReasonAndTheMoment() {
        Location location = Location.define("Piso 3");

        location.retire("La torre se cerró por remodelación", clock);

        assertThat(location.status()).isEqualTo(
                new CatalogueStatus.Retired("La torre se cerró por remodelación", Instant.parse("2026-09-20T10:15:30Z")));
        assertThat(location.status().usable()).isFalse();
    }

    @Test
    void restoringClearsTheReason() {
        Location location = Location.define("Piso 3");
        location.retire("Remodelación", clock);

        location.restore();

        assertThat(location.status()).isInstanceOf(CatalogueStatus.Active.class);
    }

    @Test
    void anEntryCannotBeRetiredTwiceNorRestoredWhileActive() {
        Location location = Location.define("Piso 3");

        assertThatThrownBy(location::restore).isInstanceOf(AdmissionsException.AlreadyActive.class);

        location.retire("Remodelación", clock);
        assertThatThrownBy(() -> location.retire("Otra vez", clock))
                .isInstanceOf(AdmissionsException.AlreadyRetired.class);
    }

    @Test
    void retiringDemandsAReason() {
        Location location = Location.define("Piso 3");

        assertThatThrownBy(() -> location.retire("  ", clock)).isInstanceOf(AdmissionsException.InvalidData.class);
    }

    @Test
    void renamingReportsWhetherAnythingChanged() {
        Location location = Location.define("Piso 3");

        assertThat(location.rename("Piso 3")).isFalse();
        assertThat(location.rename("Piso 3 - Ala norte")).isTrue();
        assertThat(location.name()).isEqualTo("Piso 3 - Ala norte");
    }

    @Test
    void namesAreTrimmedAndCollapsed() {
        assertThat(Location.define("  Piso   3  ").name()).isEqualTo("Piso 3");
    }

    @ParameterizedTest
    @EnumSource(AdmissionKind.class)
    void onlyInpatientCareNeedsABed(AdmissionKind kind) {
        assertThat(kind.bedRequired()).isEqualTo(kind == AdmissionKind.INPATIENT);
    }

    @ParameterizedTest
    @EnumSource(AdmissionKind.class)
    void coverageNeverBlocksAnEmergency(AdmissionKind kind) {
        assertThat(kind.coverageMayBlockAdmission()).isEqualTo(kind != AdmissionKind.EMERGENCY);
    }

    @Test
    void aRetiredServiceTypeCannotBeConfiguredAnywhere() {
        ServiceType hospitalisation = ServiceType.define("Hospitalización", AdmissionKind.INPATIENT);
        hospitalisation.retire("Se unificó con UCI", clock);

        assertThatThrownBy(() -> com.ClinicaDeYmid.admissions_service.domain.ConfigurationService
                .configure(hospitalisation, Location.define("Piso 3")))
                .isInstanceOf(AdmissionsException.RetiredServiceType.class);
    }

    @Test
    void aRetiredLocationCannotHostAService() {
        Location location = Location.define("Piso 3");
        location.retire("Remodelación", clock);

        assertThatThrownBy(() -> com.ClinicaDeYmid.admissions_service.domain.ConfigurationService
                .configure(ServiceType.define("Hospitalización", AdmissionKind.INPATIENT), location))
                .isInstanceOf(AdmissionsException.RetiredLocation.class);
    }
}
