package com.ClinicaDeYmid.contracting_service.domain;

import com.ClinicaDeYmid.contracting_service.support.PayerFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioItemTest {

    @Test
    void offersAServiceWithBothCodes() {
        PortfolioItem item = offered();

        assertThat(item.code().cups()).isEqualTo("890201");
        assertThat(item.code().clinic()).isEqualTo("CON-001");
        assertThat(item.status().offered()).isTrue();
    }

    @Test
    void normalisesTheClinicCode() {
        PortfolioItem item = PortfolioItem.offer(new ServiceCode("890201", "con-001"), "Consulta de medicina general",
                ServiceCategory.CONSULTATION);

        assertThat(item.code().clinic()).isEqualTo("CON-001");
    }

    @ParameterizedTest
    @CsvSource({
            "89020, CON-001",
            "890201999, CON-001",
            "89A201, CON-001",
            "890201, 'CON 001'",
            "890201, C"
    })
    void rejectsCodesThatDoNotFollowTheFormat(String cups, String clinic) {
        assertThatThrownBy(() -> new ServiceCode(cups, clinic)).isInstanceOf(ContractingException.InvalidData.class);
    }

    @Test
    void reportsWhetherTheDescriptionActuallyChanged() {
        PortfolioItem item = offered();

        assertThat(item.describe(new ServiceCode("890201", "CON-001"), "Consulta de medicina general",
                ServiceCategory.CONSULTATION)).isFalse();
        assertThat(item.describe(new ServiceCode("890301", "CON-001"), "Consulta de medicina general",
                ServiceCategory.CONSULTATION)).isTrue();
        assertThat(item.code().cups()).isEqualTo("890301");
    }

    @Test
    void stopsAndResumesBeingOffered() {
        PortfolioItem item = offered();

        item.stopOffering("La clínica dejó de habilitar el servicio", PayerFixtures.CLOCK);

        assertThat(item.status().offered()).isFalse();
        assertThatThrownBy(() -> item.stopOffering("Otra vez", PayerFixtures.CLOCK))
                .isInstanceOf(ContractingException.PortfolioItemNotOffered.class);

        item.offerAgain();

        assertThat(item.status()).isEqualTo(new PortfolioItemStatus.Active());
        assertThatThrownBy(item::offerAgain).isInstanceOf(ContractingException.PortfolioItemAlreadyOffered.class);
    }

    private static PortfolioItem offered() {
        return PortfolioItem.offer(new ServiceCode("890201", "CON-001"), "Consulta de medicina general",
                ServiceCategory.CONSULTATION);
    }
}
