package com.ClinicaDeYmid.patient_service.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityDocumentTest {

    @Test
    void removesSeparatorsFromNumericDocuments() {
        assertThat(new IdentityDocument(DocumentType.CEDULA_DE_CIUDADANIA, " 1.098.765-432 ").number())
                .isEqualTo("1098765432");
    }

    @Test
    void normalizesAlphanumericDocumentsToUpperCase() {
        assertThat(new IdentityDocument(DocumentType.PASAPORTE, "ab 123456").number()).isEqualTo("AB123456");
    }

    @Test
    void rejectsLettersInNumericDocuments() {
        assertThatThrownBy(() -> new IdentityDocument(DocumentType.CEDULA_DE_CIUDADANIA, "10A8765"))
                .isInstanceOf(PatientException.InvalidData.class)
                .hasMessageContaining("document.number");
    }

    @Test
    void requiresTypeAndNumber() {
        assertThatThrownBy(() -> new IdentityDocument(null, "1098765432")).isInstanceOf(PatientException.InvalidData.class);
        assertThatThrownBy(() -> new IdentityDocument(DocumentType.PASAPORTE, "  ")).isInstanceOf(PatientException.InvalidData.class);
    }

    @ParameterizedTest
    @CsvSource({
            "REGISTRO_CIVIL, 0, true",
            "REGISTRO_CIVIL, 6, true",
            "REGISTRO_CIVIL, 7, false",
            "TARJETA_DE_IDENTIDAD, 6, false",
            "TARJETA_DE_IDENTIDAD, 7, true",
            "TARJETA_DE_IDENTIDAD, 17, true",
            "TARJETA_DE_IDENTIDAD, 18, false",
            "CEDULA_DE_CIUDADANIA, 17, false",
            "CEDULA_DE_CIUDADANIA, 18, true",
            "PASAPORTE, 2, true",
            "CEDULA_DE_EXTRANJERIA, 70, true"
    })
    void matchesDocumentTypeWithAge(DocumentType type, int age, boolean accepted) {
        IdentityDocument document = new IdentityDocument(type, "12345678");

        if (accepted) {
            assertThatCode(() -> document.assertValidForAge(age)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> document.assertValidForAge(age)).isInstanceOf(PatientException.DocumentNotValidForAge.class);
        }
    }

    @Test
    void neverExposesTheFullNumberWhenPrinted() {
        IdentityDocument document = new IdentityDocument(DocumentType.CEDULA_DE_CIUDADANIA, "1098765432");

        assertThat(document.toString())
                .isEqualTo("CEDULA_DE_CIUDADANIA:******5432")
                .doesNotContain("1098765432");
    }
}
