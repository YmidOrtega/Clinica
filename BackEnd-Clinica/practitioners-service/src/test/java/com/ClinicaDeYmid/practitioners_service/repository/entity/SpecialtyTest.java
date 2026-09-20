package com.ClinicaDeYmid.practitioners_service.repository.entity;

import com.ClinicaDeYmid.practitioners_service.shared.PractitionersException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpecialtyTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-19T15:00:00Z"), ZoneOffset.UTC);

    @Test
    void registersWithTheCodeInUpperCaseAndActive() {
        Specialty specialty = Specialty.register(" cardio ", "  Cardiología   clínica ");

        assertThat(specialty.code()).isEqualTo("CARDIO");
        assertThat(specialty.name()).isEqualTo("Cardiología clínica");
        assertThat(specialty.status().active()).isTrue();
        assertThat(specialty.uuid()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-CARDIO", "CARDIO LOGIA", "C", "CARDIO/1", "CARDIOLOGIA-DEMASIADO-LARGA"})
    void refusesCodesThatAreNotCatalogueCodes(String code) {
        assertThatThrownBy(() -> Specialty.register(code, "Cardiología"))
                .isInstanceOf(PractitionersException.InvalidData.class);
    }

    @Test
    void refusesNamesThatSayNothing() {
        assertThatThrownBy(() -> Specialty.register("CARDIO", "  "))
                .isInstanceOf(PractitionersException.InvalidData.class);
        assertThatThrownBy(() -> Specialty.register("CARDIO", "ab"))
                .isInstanceOf(PractitionersException.InvalidData.class);
    }

    @Test
    void renamingReportsWhetherSomethingChanged() {
        Specialty specialty = Specialty.register("CARDIO", "Cardiología");

        assertThat(specialty.rename("Cardiología")).isFalse();
        assertThat(specialty.rename("Cardiología clínica")).isTrue();
        assertThat(specialty.name()).isEqualTo("Cardiología clínica");
    }

    @Test
    void deactivatingDragsItsActiveSubSpecialties() {
        Specialty specialty = Specialty.register("CARDIO", "Cardiología");
        SubSpecialty hemodynamics = specialty.add("HEMO", "Hemodinamia");
        SubSpecialty electrophysiology = specialty.add("ELECTRO", "Electrofisiología");
        electrophysiology.deactivate("Ya no se presta el servicio", CLOCK);

        specialty.deactivate("La clínica cerró el servicio", CLOCK);

        assertThat(specialty.status().active()).isFalse();
        assertThat(hemodynamics.status()).isEqualTo(
                new CatalogueStatus.Inactive("La clínica cerró el servicio", Instant.now(CLOCK)));
        assertThat(electrophysiology.status()).isEqualTo(
                new CatalogueStatus.Inactive("Ya no se presta el servicio", Instant.now(CLOCK)));
    }

    @Test
    void refusesToDeactivateTwiceOrToReactivateWhatIsActive() {
        Specialty specialty = Specialty.register("CARDIO", "Cardiología");

        assertThatThrownBy(specialty::reactivate).isInstanceOf(PractitionersException.AlreadyActive.class);
        specialty.deactivate("La clínica cerró el servicio", CLOCK);
        assertThatThrownBy(() -> specialty.deactivate("Otra razón cualquiera", CLOCK))
                .isInstanceOf(PractitionersException.NotActive.class);
    }

    @Test
    void refusesShortReasonsAndSubSpecialtiesOnInactiveSpecialties() {
        Specialty specialty = Specialty.register("CARDIO", "Cardiología");

        assertThatThrownBy(() -> specialty.deactivate("corta", CLOCK))
                .isInstanceOf(PractitionersException.InvalidData.class);

        specialty.deactivate("La clínica cerró el servicio", CLOCK);
        assertThatThrownBy(() -> specialty.add("HEMO", "Hemodinamia"))
                .isInstanceOf(PractitionersException.SpecialtyNotActiveForSubSpecialty.class);
    }

    @Test
    void aSubSpecialtyOnlyComesBackIfItsSpecialtyIsActive() {
        Specialty specialty = Specialty.register("CARDIO", "Cardiología");
        SubSpecialty hemodynamics = specialty.add("HEMO", "Hemodinamia");
        specialty.deactivate("La clínica cerró el servicio", CLOCK);

        assertThatThrownBy(hemodynamics::reactivate)
                .isInstanceOf(PractitionersException.SpecialtyNotActiveForSubSpecialty.class);

        specialty.reactivate();
        hemodynamics.reactivate();

        assertThat(hemodynamics.status().active()).isTrue();
    }
}
