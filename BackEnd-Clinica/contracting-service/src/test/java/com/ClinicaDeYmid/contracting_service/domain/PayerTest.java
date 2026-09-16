package com.ClinicaDeYmid.contracting_service.domain;

import com.ClinicaDeYmid.contracting_service.support.PayerFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayerTest {

    @Test
    void registersAnActivePayerWithItsOwnIdentifier() {
        Payer payer = PayerFixtures.active();

        assertThat(payer.uuid()).isNotNull();
        assertThat(payer.status()).isEqualTo(new PayerStatus.Active());
        assertThat(payer.status().contractable()).isTrue();
        assertThat(payer.nit().formatted()).isEqualTo("901234567-7");
        assertThat(payer.type()).isEqualTo(PayerType.EPS);
    }

    @Test
    void correctsItsIdentityAndItsContact() {
        Payer payer = PayerFixtures.active();

        payer.correctIdentity(new PayerIdentity("Salud Total EPS-S S.A.", new Nit("890903938"), PayerType.EPS, "eps002"));
        payer.updateContact(new ContactInfo("Carrera 7 # 71-21", "6013456789", "FACTURAS@saludtotal.test"));

        assertThat(payer.socialReason()).isEqualTo("Salud Total EPS-S S.A.");
        assertThat(payer.nit().number()).isEqualTo("890903938");
        assertThat(payer.adresCode()).isEqualTo("EPS002");
        assertThat(payer.contact().billingEmail()).isEqualTo("facturas@saludtotal.test");
    }

    @Test
    void suspendsAndReactivatesWithoutLosingTheReason() {
        Payer payer = PayerFixtures.active();

        payer.suspend("Suspensión por cartera vencida", PayerFixtures.CLOCK);

        assertThat(payer.status()).isInstanceOf(PayerStatus.Suspended.class);
        assertThat(payer.status().contractable()).isFalse();
        assertThat(((PayerStatus.Suspended) payer.status()).reason()).isEqualTo("Suspensión por cartera vencida");

        payer.reactivate(PayerFixtures.CLOCK);

        assertThat(payer.status()).isEqualTo(new PayerStatus.Active());
    }

    @Test
    void rejectsRepeatingTheSameTransition() {
        Payer payer = PayerFixtures.active();

        assertThatThrownBy(() -> payer.reactivate(PayerFixtures.CLOCK))
                .isInstanceOf(ContractingException.InvalidStatusTransition.class);

        payer.deactivate("Liquidación de la entidad", PayerFixtures.CLOCK);

        assertThatThrownBy(() -> payer.deactivate("Otra vez", PayerFixtures.CLOCK))
                .isInstanceOf(ContractingException.InvalidStatusTransition.class);
    }

    @Test
    void refusesToChangeADeactivatedPayerUntilItComesBack() {
        Payer payer = PayerFixtures.active();
        payer.deactivate("Liquidación de la entidad", PayerFixtures.CLOCK);

        assertThatThrownBy(() -> payer.updateContact(new ContactInfo("Calle 1", "6011111111", null)))
                .isInstanceOf(ContractingException.PayerNotActive.class);

        payer.reactivate(PayerFixtures.CLOCK);
        payer.updateContact(new ContactInfo("Calle 1", "6011111111", null));

        assertThat(payer.contact().address()).isEqualTo("Calle 1");
    }

    @Test
    void demandsAReasonLongEnoughToExplainTheChange() {
        Payer payer = PayerFixtures.active();

        assertThatThrownBy(() -> payer.suspend("  ", PayerFixtures.CLOCK))
                .isInstanceOf(ContractingException.InvalidData.class);
    }
}
