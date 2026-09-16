package com.ClinicaDeYmid.contracting_service.application;

import com.ClinicaDeYmid.contracting_service.domain.ContractingException;
import com.ClinicaDeYmid.contracting_service.domain.PortfolioItem;
import com.ClinicaDeYmid.contracting_service.domain.PortfolioItems;
import com.ClinicaDeYmid.contracting_service.domain.ServiceCategory;
import com.ClinicaDeYmid.contracting_service.domain.ServiceCode;
import com.ClinicaDeYmid.contracting_service.support.InMemoryPortfolioItems;
import com.ClinicaDeYmid.contracting_service.support.PayerFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioCommandsTest {

    private PortfolioItems items;
    private PortfolioCommands commands;

    @BeforeEach
    void setUp() {
        items = new InMemoryPortfolioItems();
        commands = new PortfolioCommands(items, TransactionOperations.withoutTransaction(), PayerFixtures.CLOCK);
    }

    @Test
    void keepsTheClinicCodeUnique() {
        commands.offer(new ServiceCode("890201", "CON-001"), "Consulta de medicina general", ServiceCategory.CONSULTATION);

        assertThatThrownBy(() -> commands.offer(new ServiceCode("890301", "CON-001"), "Consulta de pediatría",
                ServiceCategory.CONSULTATION))
                .isInstanceOf(ContractingException.ClinicCodeAlreadyUsed.class);
    }

    @Test
    void importingTheSameFileTwiceChangesNothingTheSecondTime() {
        List<PortfolioCommands.Entry> file = List.of(
                new PortfolioCommands.Entry(new ServiceCode("890201", "CON-001"), "Consulta de medicina general", ServiceCategory.CONSULTATION),
                new PortfolioCommands.Entry(new ServiceCode("903841", "LAB-014"), "Hemograma IV", ServiceCategory.LABORATORY));

        assertThat(commands.importAll(file)).isEqualTo(new PortfolioCommands.Import(2, 0, 0));
        assertThat(commands.importAll(file)).isEqualTo(new PortfolioCommands.Import(0, 0, 2));
    }

    @Test
    void importingCorrectsOnlyWhatChanged() {
        commands.importAll(List.of(
                new PortfolioCommands.Entry(new ServiceCode("890201", "CON-001"), "Consulta general", ServiceCategory.CONSULTATION),
                new PortfolioCommands.Entry(new ServiceCode("903841", "LAB-014"), "Hemograma IV", ServiceCategory.LABORATORY)));

        PortfolioCommands.Import result = commands.importAll(List.of(
                new PortfolioCommands.Entry(new ServiceCode("890201", "CON-001"), "Consulta de medicina general", ServiceCategory.CONSULTATION),
                new PortfolioCommands.Entry(new ServiceCode("903841", "LAB-014"), "Hemograma IV", ServiceCategory.LABORATORY),
                new PortfolioCommands.Entry(new ServiceCode("881235", "ECO-002"), "Ecografía abdominal total", ServiceCategory.IMAGING)));

        assertThat(result).isEqualTo(new PortfolioCommands.Import(1, 1, 1));
        assertThat(items.findByClinicCode("CON-001").orElseThrow().name()).isEqualTo("Consulta de medicina general");
    }

    @Test
    void refusesToMoveAClinicCodeToAnotherService() {
        PortfolioItem first = commands.offer(new ServiceCode("890201", "CON-001"), "Consulta de medicina general",
                ServiceCategory.CONSULTATION);
        commands.offer(new ServiceCode("903841", "LAB-014"), "Hemograma IV", ServiceCategory.LABORATORY);

        assertThatThrownBy(() -> commands.describe(first.uuid(), first.version(), new ServiceCode("890201", "LAB-014"),
                "Consulta de medicina general", ServiceCategory.CONSULTATION))
                .isInstanceOf(ContractingException.ClinicCodeAlreadyUsed.class);
    }
}
