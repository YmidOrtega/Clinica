package com.ClinicaDeYmid.billing_service.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IssuerProfileTest {

    @Test
    void normalisesTheProfileAndDerivesTheDepartment() {
        IssuerProfile profile = profile(Set.of(TaxResponsibility.SELF_WITHHOLDER, TaxResponsibility.LARGE_TAXPAYER),
                "05001", "  Facturacion@Clinica.co ");

        assertThat(profile.departmentCode()).isEqualTo("05");
        assertThat(profile.email()).isEqualTo("facturacion@clinica.co");
        assertThat(TaxResponsibility.joined(profile.taxResponsibilities())).isEqualTo("O-13;O-15");
    }

    @Test
    void notResponsibleCannotBeCombinedWithOtherResponsibilities() {
        assertThatThrownBy(() -> profile(EnumSet.of(TaxResponsibility.NOT_RESPONSIBLE, TaxResponsibility.LARGE_TAXPAYER),
                "05001", "facturacion@clinica.co"))
                .isInstanceOf(BillingException.InvalidData.class)
                .hasMessageContaining("R-99-PN");
    }

    @Test
    void needsAtLeastOneResponsibility() {
        assertThatThrownBy(() -> profile(Set.of(), "05001", "facturacion@clinica.co"))
                .isInstanceOf(BillingException.InvalidData.class);
    }

    @Test
    void refusesAMunicipalityThatIsNotADaneCode() {
        assertThatThrownBy(() -> profile(Set.of(TaxResponsibility.LARGE_TAXPAYER), "Medellín", "facturacion@clinica.co"))
                .isInstanceOf(BillingException.InvalidData.class)
                .hasMessageContaining("DANE");
    }

    @Test
    void readsTheDianCodesBack() {
        assertThat(TaxResponsibility.ofDianCode("O-47")).isEqualTo(TaxResponsibility.SIMPLE_TAX_REGIME);
        assertThatThrownBy(() -> TaxResponsibility.ofDianCode("O-99")).isInstanceOf(BillingException.InvalidData.class);
    }

    static IssuerProfile profile(Set<TaxResponsibility> responsibilities, String municipality, String email) {
        return new IssuerProfile(PersonType.LEGAL_ENTITY, "Clínica de Ymid S.A.S.", null, TaxScheme.NOT_APPLICABLE,
                responsibilities, "Calle 10 # 43-20", municipality, "Medellín", "Antioquia", null, email,
                "6044441234", "050010123401");
    }
}
