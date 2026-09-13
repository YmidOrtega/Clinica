package com.ClinicaDeYmid.patient_service.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientStatusTest {

    private static final Instant NOW = Instant.parse("2026-09-13T15:00:00Z");
    private static final LocalDate DEATH = LocalDate.of(2026, 9, 1);

    private final PatientStatus active = new PatientStatus.Active();
    private final PatientStatus inactive = new PatientStatus.Inactive("Registro duplicado", NOW);
    private final PatientStatus deceased = new PatientStatus.Deceased(DEATH);

    @Test
    void activePatientsCanBeDeactivatedOrDie() {
        assertThat(active.deactivate("Registro duplicado", NOW)).isEqualTo(new PatientStatus.Inactive("Registro duplicado", NOW));
        assertThat(active.die(DEATH)).isEqualTo(new PatientStatus.Deceased(DEATH));
    }

    @Test
    void inactivePatientsCanBeReactivatedOrDie() {
        assertThat(inactive.reactivate()).isEqualTo(new PatientStatus.Active());
        assertThat(inactive.die(DEATH)).isInstanceOf(PatientStatus.Deceased.class);
    }

    @Test
    void deathIsFinal() {
        assertThatThrownBy(deceased::reactivate).isInstanceOf(PatientException.InvalidStatusTransition.class);
        assertThatThrownBy(() -> deceased.deactivate("x", NOW)).isInstanceOf(PatientException.InvalidStatusTransition.class);
        assertThatThrownBy(() -> deceased.die(DEATH)).isInstanceOf(PatientException.InvalidStatusTransition.class);
    }

    @Test
    void rejectsRedundantTransitions() {
        assertThatThrownBy(active::reactivate).isInstanceOf(PatientException.InvalidStatusTransition.class);
        assertThatThrownBy(() -> inactive.deactivate("x", NOW)).isInstanceOf(PatientException.InvalidStatusTransition.class);
    }

    @Test
    void deactivationRequiresAReason() {
        assertThatThrownBy(() -> active.deactivate(" ", NOW)).isInstanceOf(PatientException.InvalidData.class);
    }

    @Test
    void exposesAStableCodePerVariant() {
        assertThat(active.code()).isEqualTo(PatientStatus.Code.ACTIVE);
        assertThat(inactive.code()).isEqualTo(PatientStatus.Code.INACTIVE);
        assertThat(deceased.code()).isEqualTo(PatientStatus.Code.DECEASED);
    }
}
