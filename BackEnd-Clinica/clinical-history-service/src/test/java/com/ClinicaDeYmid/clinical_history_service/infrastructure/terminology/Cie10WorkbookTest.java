package com.ClinicaDeYmid.clinical_history_service.infrastructure.terminology;

import com.ClinicaDeYmid.clinical_history_service.domain.terminology.Concept;
import com.ClinicaDeYmid.clinical_history_service.support.Cie10WorkbookFixture;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.ClinicaDeYmid.clinical_history_service.support.Cie10WorkbookFixture.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Cie10WorkbookTest {

    @Test
    void readsTheSisproTableFromTheFinalSheet() {
        Cie10Workbook.Parsed parsed = Cie10Workbook.parse(Cie10WorkbookFixture.standard());

        assertThat(parsed.version()).isEqualTo("2021-02-08");
        assertThat(parsed.concepts()).extracting(Concept::code).containsExactly("A000", "E119", "I10X", "I700", "J459");
        assertThat(parsed.concepts().get(2)).isEqualTo(new Concept("I10X", "Hipertension esencial (primaria)", "I10",
                "Hipertension Esencial (Primaria)", 9, "Enfermedades del sistema circulatorio (I00-I99)"));
    }

    @Test
    void takesTheCategoryFromTheCodeWhenTheFileMisfilesIt() {
        Cie10Workbook.Parsed parsed = Cie10Workbook.parse(Cie10WorkbookFixture.standard());

        assertThat(parsed.concepts().get(3).categoryCode()).isEqualTo("I70");
        assertThat(parsed.concepts().get(3).categoryDisplay()).isNull();
        assertThat(parsed.warnings()).containsExactly("El código I700 aparece bajo la categoría I69; se usa I70 y queda sin título de categoría");
    }

    @Test
    void rejectsFilesThatAreNotTheOfficialTable() {
        assertThatThrownBy(() -> Cie10Workbook.parse("no es un excel".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidWorkbookException.class);
        assertThatThrownBy(() -> Cie10Workbook.parse(Cie10WorkbookFixture.workbook("08-02-2021", List.<String[]>of(
                row("9", "Circulatorio", "I10", "Hipertension", "I10X", "Hipertension esencial"),
                row("9", "Circulatorio", "I10", "Hipertension", "I10X", "Duplicado")))))
                .hasMessageContaining("I10X aparece repetido");
        assertThatThrownBy(() -> Cie10Workbook.parse(Cie10WorkbookFixture.workbook("08-02-2021", List.<String[]>of(
                row("9", "Circulatorio", "I10", "Hipertension", "I10", "Codigo de tres caracteres")))))
                .hasMessageContaining("no tiene el formato");
        assertThatThrownBy(() -> Cie10Workbook.parse(Cie10WorkbookFixture.workbook("31-02-2021", List.<String[]>of(
                row("9", "Circulatorio", "I10", "Hipertension", "I10X", "Hipertension esencial")))))
                .hasMessageContaining("fecha de actualización");
    }
}
