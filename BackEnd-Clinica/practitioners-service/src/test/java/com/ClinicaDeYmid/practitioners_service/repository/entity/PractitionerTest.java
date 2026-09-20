package com.ClinicaDeYmid.practitioners_service.repository.entity;

import com.ClinicaDeYmid.practitioners_service.shared.DocumentType;
import com.ClinicaDeYmid.practitioners_service.shared.PractitionersException;
import com.ClinicaDeYmid.practitioners_service.shared.RelationshipType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PractitionerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-19T15:00:00Z"), ZoneOffset.UTC);

    @Test
    void registersActiveWithItsDocumentAndRegistration() {
        Practitioner practitioner = practitioner();

        assertThat(practitioner.uuid()).isNotNull();
        assertThat(practitioner.status().attends()).isTrue();
        assertThat(practitioner.fullName()).isEqualTo("Ana María Restrepo Gómez");
        assertThat(practitioner.registration().number()).isEqualTo("RM-12345");
        assertThat(practitioner.contact().email()).isEqualTo("ana.restrepo@clinica.local");
    }

    @ParameterizedTest
    @CsvSource({"CEDULA_DE_CIUDADANIA,12AB3456", "CEDULA_DE_CIUDADANIA,12", "PASAPORTE,AB1"})
    void refusesDocumentsThatDoNotMatchTheirType(DocumentType type, String number) {
        assertThatThrownBy(() -> new IdentityDocument(type, number))
                .isInstanceOf(PractitionersException.InvalidData.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana.restrepo", "ana@clinica", "ana restrepo@clinica.local"})
    void refusesMalformedEmails(String email) {
        assertThatThrownBy(() -> new ContactInfo(email, "3001234567", null))
                .isInstanceOf(PractitionersException.InvalidData.class);
    }

    @Test
    void refusesMobilesThatAreNotColombianAndRegistrationsInTheFuture() {
        assertThatThrownBy(() -> new ContactInfo("ana@clinica.local", "6012223344", null))
                .isInstanceOf(PractitionersException.InvalidData.class);
        assertThatThrownBy(() -> new ProfessionalRegistration("RM-12345", LocalDate.now(CLOCK).plusDays(1), CLOCK))
                .isInstanceOf(PractitionersException.InvalidData.class);
    }

    @Test
    void movesBetweenStatesWithItsReasons() {
        Practitioner practitioner = practitioner();

        practitioner.suspend("Investigación disciplinaria en curso", CLOCK);
        assertThat(practitioner.status()).isEqualTo(
                new PractitionerStatus.Suspended("Investigación disciplinaria en curso", Instant.now(CLOCK)));
        assertThatThrownBy(() -> practitioner.suspend("Otra razón cualquiera", CLOCK))
                .isInstanceOf(PractitionersException.InvalidStatusTransition.class);

        practitioner.reinstate();
        assertThat(practitioner.status().attends()).isTrue();

        practitioner.retire("Terminó su contrato con la clínica", CLOCK);
        assertThatThrownBy(() -> practitioner.retire("Terminó su contrato con la clínica", CLOCK))
                .isInstanceOf(PractitionersException.InvalidStatusTransition.class);

        practitioner.reinstate();
        assertThat(practitioner.status().attends()).isTrue();
    }

    @Test
    void demandsExactlyOnePrincipalSpecialty() {
        Practitioner practitioner = practitioner();
        Specialty cardiology = Specialty.register("CARDIO", "Cardiología");
        Specialty internal = Specialty.register("INTERNA", "Medicina interna");

        assertThatThrownBy(() -> practitioner.assign(List.of(), CLOCK))
                .isInstanceOf(PractitionersException.PrincipalSpecialtyRequired.class);
        assertThatThrownBy(() -> practitioner.assign(List.of(
                new Practitioner.Assignment(cardiology, null, true),
                new Practitioner.Assignment(internal, null, true)), CLOCK))
                .isInstanceOf(PractitionersException.PrincipalSpecialtyRequired.class);

        practitioner.assign(List.of(new Practitioner.Assignment(cardiology, null, true),
                new Practitioner.Assignment(internal, null, false)), CLOCK);

        assertThat(practitioner.specialties()).hasSize(2);
        assertThat(practitioner.specialties().stream().filter(PractitionerSpecialty::principal)).hasSize(1);
    }

    @Test
    void refusesSpecialtiesThatAreNotActiveOrSubSpecialtiesFromElsewhere() {
        Practitioner practitioner = practitioner();
        Specialty cardiology = Specialty.register("CARDIO", "Cardiología");
        Specialty internal = Specialty.register("INTERNA", "Medicina interna");
        SubSpecialty hemodynamics = cardiology.add("HEMO", "Hemodinamia");

        assertThatThrownBy(() -> practitioner.assign(List.of(
                new Practitioner.Assignment(internal, hemodynamics, true)), CLOCK))
                .isInstanceOf(PractitionersException.SubSpecialtyOutsideSpecialty.class);

        internal.deactivate("La clínica cerró el servicio", CLOCK);
        assertThatThrownBy(() -> practitioner.assign(List.of(new Practitioner.Assignment(internal, null, true)), CLOCK))
                .isInstanceOf(PractitionersException.SpecialtyNotActiveForPractitioner.class);

        practitioner.assign(List.of(new Practitioner.Assignment(cardiology, hemodynamics, true)), CLOCK);
        assertThat(practitioner.specialties().getFirst().subSpecialty()).isEqualTo(hemodynamics);
    }

    private static Practitioner practitioner() {
        return Practitioner.register(new IdentityDocument(DocumentType.CEDULA_DE_CIUDADANIA, "1098765432"),
                " Ana María ", "Restrepo  Gómez",
                new ProfessionalRegistration("rm-12345", LocalDate.of(2015, 3, 1), CLOCK),
                new ContactInfo("Ana.Restrepo@clinica.local", "3001234567", "6076112233"),
                RelationshipType.STAFF);
    }
}
