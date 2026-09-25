package com.ClinicaDeYmid.billing_service.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NitTest {

    @ParameterizedTest
    @CsvSource({"800197268, 4", "860034313, 7", "899999063, 3", "900373913, 4"})
    void computesTheVerificationDigitTheDianPublishes(String number, int digit) {
        assertThat(Nit.verificationDigitOf(number)).isEqualTo(digit);
        assertThat(new Nit(number, digit).formatted()).isEqualTo(number + "-" + digit);
    }

    @Test
    void refusesAVerificationDigitThatDoesNotMatch() {
        assertThatThrownBy(() -> new Nit("800197268", 5))
                .isInstanceOf(BillingException.WrongVerificationDigit.class)
                .hasMessageContaining("debería ser 4");
    }

    @ParameterizedTest
    @ValueSource(strings = {"800.197.268", "800197268-4", "12345", "01234567", "12345678901", " "})
    void refusesSomethingThatIsNotANit(String number) {
        assertThatThrownBy(() -> new Nit(number, 0)).isInstanceOf(BillingException.InvalidData.class);
    }
}
