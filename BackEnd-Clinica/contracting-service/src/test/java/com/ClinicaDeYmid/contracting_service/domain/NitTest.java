package com.ClinicaDeYmid.contracting_service.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NitTest {

    @ParameterizedTest
    @CsvSource({
            "890903938, 890903938-8",
            "899999068, 899999068-1",
            "860002964, 860002964-4",
            "901234567, 901234567-7"
    })
    void computesTheVerificationDigit(String number, String formatted) {
        assertThat(new Nit(number).formatted()).isEqualTo(formatted);
    }

    @Test
    void acceptsTheDigitWhenItMatchesAndCleansSeparators() {
        assertThat(new Nit("890.903.938-8").number()).isEqualTo("890903938");
        assertThat(new Nit("890 903 938").verificationDigit()).isEqualTo(8);
    }

    @Test
    void rejectsAWrongVerificationDigit() {
        assertThatThrownBy(() -> new Nit("890903938-9"))
                .isInstanceOf(ContractingException.InvalidData.class)
                .hasMessageContaining("dígito de verificación");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678", "9012345678901", "abcdefghi", "901234567-", "-3"})
    void rejectsMalformedNumbers(String value) {
        assertThatThrownBy(() -> new Nit(value)).isInstanceOf(ContractingException.InvalidData.class);
    }

    @Test
    void comparesByNumber() {
        assertThat(new Nit("890903938")).isEqualTo(new Nit("890.903.938-8"));
        assertThat(new Nit("890903938")).isNotEqualTo(new Nit("899999068"));
    }
}
