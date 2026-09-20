package com.ClinicaDeYmid.practitioners_service;

import com.ClinicaDeYmid.commons.web.EntityTags;
import com.ClinicaDeYmid.practitioners_service.service.CatalogueCommands.CatalogueEntry;
import com.ClinicaDeYmid.practitioners_service.service.CatalogueCommands.NewSpecialty;
import com.ClinicaDeYmid.practitioners_service.service.CatalogueCommands.NewSubSpecialty;
import com.ClinicaDeYmid.practitioners_service.service.CatalogueCommands.StatusFilter;
import com.ClinicaDeYmid.practitioners_service.service.CatalogueService;
import com.ClinicaDeYmid.practitioners_service.service.CatalogueViews.ImportSummary;
import com.ClinicaDeYmid.practitioners_service.service.CatalogueViews.SpecialtyView;
import com.ClinicaDeYmid.practitioners_service.service.CatalogueViews.SubSpecialtyView;
import com.ClinicaDeYmid.practitioners_service.shared.PractitionersException;
import com.ClinicaDeYmid.practitioners_service.support.JwtTestTokens;
import com.ClinicaDeYmid.practitioners_service.support.MySqlTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(MySqlTestContainer.class)
class CatalogueServiceIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private CatalogueService catalogue;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
    }

    @Test
    void registersASpecialtyAndRefusesARepeatedCode() {
        String code = nextCode();
        SpecialtyView registered = catalogue.register(new NewSpecialty(code.toLowerCase(), "Cardiología"));

        assertThat(registered.code()).isEqualTo(code);
        assertThat(registered.version()).isZero();
        assertThat(registered.status().active()).isTrue();

        assertThatThrownBy(() -> catalogue.register(new NewSpecialty(code, "Otro nombre")))
                .isInstanceOf(PractitionersException.SpecialtyCodeAlreadyUsed.class);
    }

    @Test
    void keepsTheHistoryOfEveryChangeInEnvers() {
        SpecialtyView registered = catalogue.register(new NewSpecialty(nextCode(), "Cardiología"));
        catalogue.rename(registered.uuid(), registered.version(), "Cardiología clínica");

        Integer revisions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM practitioners_history.specialties_aud a "
                        + "JOIN specialties s ON s.id = a.id WHERE s.uuid = ?", Integer.class, registered.uuid().toString());

        assertThat(revisions).isEqualTo(2);
    }

    @Test
    void refusesToChangeWithAnOutdatedVersion() {
        SpecialtyView registered = catalogue.register(new NewSpecialty(nextCode(), "Cardiología"));

        assertThatThrownBy(() -> catalogue.rename(registered.uuid(), registered.version() + 1, "Cardiología clínica"))
                .isInstanceOf(EntityTags.StaleVersion.class);
    }

    @Test
    void addsSubSpecialtiesAndReadsThemBackWithTheSpecialty() {
        SpecialtyView specialty = catalogue.register(new NewSpecialty(nextCode(), "Cardiología"));
        String subCode = nextCode();
        SubSpecialtyView added = catalogue.addSubSpecialty(specialty.uuid(), new NewSubSpecialty(subCode, "Hemodinamia"));

        assertThat(added.specialtyUuid()).isEqualTo(specialty.uuid());
        assertThat(catalogue.get(specialty.uuid()).subSpecialties())
                .extracting(SubSpecialtyView::code).containsExactly(subCode);

        assertThatThrownBy(() -> catalogue.addSubSpecialty(specialty.uuid(), new NewSubSpecialty(subCode, "Otra")))
                .isInstanceOf(PractitionersException.SubSpecialtyCodeAlreadyUsed.class);
    }

    @Test
    void deactivatingASpecialtyAlsoClosesItsSubSpecialties() {
        SpecialtyView specialty = catalogue.register(new NewSpecialty(nextCode(), "Cardiología"));
        catalogue.addSubSpecialty(specialty.uuid(), new NewSubSpecialty(nextCode(), "Hemodinamia"));

        SpecialtyView deactivated = catalogue.deactivate(specialty.uuid(), specialty.version(),
                "La clínica dejó de prestar el servicio");

        assertThat(deactivated.status().active()).isFalse();
        assertThat(deactivated.status().reason()).isEqualTo("La clínica dejó de prestar el servicio");
        assertThat(catalogue.get(specialty.uuid()).subSpecialties())
                .allMatch(subSpecialty -> !subSpecialty.status().active());
    }

    @Test
    void importsTheCatalogueOnceAndLeavesItIntactTheSecondTime() {
        String specialtyCode = nextCode();
        String subCode = nextCode();
        List<CatalogueEntry> entries = List.of(new CatalogueEntry(specialtyCode, "Cardiología",
                List.of(new NewSubSpecialty(subCode, "Hemodinamia"))));

        ImportSummary first = catalogue.importCatalogue(entries);
        assertThat(first).isEqualTo(new ImportSummary(1, 0, 0, 1, 0));
        assertThat(first.changedSomething()).isTrue();

        ImportSummary second = catalogue.importCatalogue(entries);
        assertThat(second).isEqualTo(new ImportSummary(0, 0, 1, 0, 0));
        assertThat(second.changedSomething()).isFalse();

        ImportSummary corrected = catalogue.importCatalogue(List.of(new CatalogueEntry(specialtyCode,
                "Cardiología clínica", List.of(new NewSubSpecialty(subCode, "Hemodinamia intervencionista")))));
        assertThat(corrected).isEqualTo(new ImportSummary(0, 1, 0, 0, 1));
    }

    @Test
    void searchesByStatusAndByNamePrefix() {
        String code = nextCode();
        SpecialtyView specialty = catalogue.register(new NewSpecialty(code, "Neurología pediátrica"));

        assertThat(catalogue.search(StatusFilter.ACTIVE, "neurolog"))
                .extracting(SpecialtyView::code).contains(code);

        catalogue.deactivate(specialty.uuid(), specialty.version(), "La clínica dejó de prestar el servicio");

        assertThat(catalogue.search(StatusFilter.ACTIVE, "neurolog"))
                .extracting(SpecialtyView::code).doesNotContain(code);
        assertThat(catalogue.search(StatusFilter.INACTIVE, null))
                .extracting(SpecialtyView::code).contains(code);
    }

    private static String nextCode() {
        return "ESP" + SEQUENCE.incrementAndGet() + "X";
    }
}
