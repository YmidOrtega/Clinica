package com.ClinicaDeYmid.billing_service.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class CufeTest {

    private static final Cufe.Input INPUT = new Cufe.Input("SETP990000000", LocalDate.parse("2026-09-27"),
            LocalTime.parse("10:15:30"), new BigDecimal("145000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("110000"), "800197268", "900156264", "fc8eac422eba16e22ffd8c6f94b3f40a6e38162c",
            DianEnvironment.TEST);

    @Test
    void concatenatesTheFieldsInTheOrderTheDianPublishes() {
        assertThat(Cufe.concatenation(INPUT)).isEqualTo("SETP990000000" + "2026-09-27" + "10:15:30-05:00"
                + "145000.00" + "01" + "0.00" + "04" + "0.00" + "03" + "0.00" + "110000.00" + "800197268" + "900156264"
                + "fc8eac422eba16e22ffd8c6f94b3f40a6e38162c" + "2");
    }

    @Test
    void isTheSha384OfThatConcatenation() {
        assertThat(Cufe.of(INPUT))
                .isEqualTo("ed633d6d3c5a31183d6c96a3afb9449095be355219a9ff5a901306a0642c928b0c1504f98597b683efdd659e399a9c6a")
                .hasSize(96);
    }

    @Test
    void theSoftwareSecurityCodeHashesTheSoftwareThePinAndTheNumber() {
        assertThat(Cufe.softwareSecurityCode("56f2ae4e-9812-4fad-9255-643406bbb1a1", "12345", "SETP990000000"))
                .isEqualTo("04b857d859779f7f2f0ca928b921a6b580d48f6045b9174d4f3f7f3da19454fca7a0b177f25219b9d716324628330adc");
    }

    @Test
    void theBuyerTravelsWithoutItsVerificationDigit() {
        assertThat(Cufe.withoutVerificationDigit("900156264-2")).isEqualTo("900156264");
        assertThat(Cufe.withoutVerificationDigit("1098765432")).isEqualTo("1098765432");
    }
}
