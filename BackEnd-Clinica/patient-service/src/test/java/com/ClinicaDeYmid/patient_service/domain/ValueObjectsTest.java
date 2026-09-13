package com.ClinicaDeYmid.patient_service.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectsTest {

    @Nested
    class PersonNames {

        @Test
        void collapsesRepeatedWhitespace() {
            PersonName name = new PersonName("  Ana   María ", "Restrepo  Gómez");

            assertThat(name.fullName()).isEqualTo("Ana María Restrepo Gómez");
        }

        @Test
        void acceptsApostrophesAndHyphens() {
            assertThat(new PersonName("D'Angelo", "Pérez-Núñez").lastNames()).isEqualTo("Pérez-Núñez");
        }

        @Test
        void rejectsDigitsAndOversizedValues() {
            assertThatThrownBy(() -> new PersonName("Ana1", "Restrepo")).isInstanceOf(PatientException.InvalidData.class);
            assertThatThrownBy(() -> new PersonName("A".repeat(101), "Restrepo")).isInstanceOf(PatientException.InvalidData.class);
        }
    }

    @Nested
    class Contacts {

        @Test
        void normalizesPhonesAndEmail() {
            ContactInfo contact = new ContactInfo("(300) 123-4567", " ", " Ana@Example.COM ");

            assertThat(contact.mobile()).isEqualTo("3001234567");
            assertThat(contact.phone()).isNull();
            assertThat(contact.email()).isEqualTo("ana@example.com");
        }

        @Test
        void requiresAValidMobile() {
            assertThatThrownBy(() -> new ContactInfo(null, null, null)).isInstanceOf(PatientException.InvalidData.class);
            assertThatThrownBy(() -> new ContactInfo("12ab", null, null)).isInstanceOf(PatientException.InvalidData.class);
        }

        @Test
        void rejectsMalformedEmail() {
            assertThatThrownBy(() -> new ContactInfo("3001234567", null, "ana@")).isInstanceOf(PatientException.InvalidData.class);
        }

        @Test
        void requiresEveryEmergencyContactField() {
            assertThatThrownBy(() -> new EmergencyContact("Luis Restrepo", null, "3017654321"))
                    .isInstanceOf(PatientException.InvalidData.class)
                    .hasMessageContaining("emergencyContact.relationship");
        }
    }

    @Nested
    class Affiliations {

        @Test
        void requiresHealthProviderWhenInsured() {
            assertThatThrownBy(() -> new Affiliation(HealthRegime.SUBSIDIZED, AffiliateType.HOLDER, null, null))
                    .isInstanceOf(PatientException.InvalidData.class)
                    .hasMessageContaining("affiliation.healthProviderNit");
        }

        @Test
        void rejectsHealthProviderForUninsuredPatients() {
            assertThatThrownBy(() -> new Affiliation(HealthRegime.UNINSURED, null, "900123456-7", null))
                    .isInstanceOf(PatientException.InvalidData.class);
            assertThat(Affiliation.uninsured().regime()).isEqualTo(HealthRegime.UNINSURED);
        }

        @Test
        void validatesNitAndNormalizesPolicy() {
            assertThatThrownBy(() -> new Affiliation(HealthRegime.CONTRIBUTORY, AffiliateType.HOLDER, "90012", null))
                    .isInstanceOf(PatientException.InvalidData.class);
            assertThat(new Affiliation(HealthRegime.CONTRIBUTORY, AffiliateType.BENEFICIARY, "900123456-7", "pol-001").policyNumber())
                    .isEqualTo("POL-001");
        }
    }

    @Nested
    class DemographicData {

        @Test
        void normalizesCountryCode() {
            assertThat(PatientFixtures.adult().countryOfOrigin()).isEqualTo("CO");
            assertThat(new Demographics(new PersonName("Ana", "Pérez"), LocalDate.of(1990, 1, 1), Sex.FEMALE, "ve", Disability.NONE)
                    .countryOfOrigin()).isEqualTo("VE");
        }

        @Test
        void rejectsImplausibleBirthDates() {
            assertThatThrownBy(() -> PatientFixtures.adultBornOn(LocalDate.of(1899, 12, 31)))
                    .isInstanceOf(PatientException.InvalidData.class);
            assertThatThrownBy(() -> PatientFixtures.adultBornOn(PatientFixtures.TODAY.plusDays(1)).ageOn(PatientFixtures.TODAY))
                    .isInstanceOf(PatientException.InvalidData.class)
                    .hasMessageContaining("birthDate");
        }

        @Test
        void requiresResidenceFields() {
            assertThatThrownBy(() -> new Residence("Santander", "Bucaramanga", null, "Calle 45"))
                    .isInstanceOf(PatientException.InvalidData.class)
                    .hasMessageContaining("residence.zone");
        }
    }
}
