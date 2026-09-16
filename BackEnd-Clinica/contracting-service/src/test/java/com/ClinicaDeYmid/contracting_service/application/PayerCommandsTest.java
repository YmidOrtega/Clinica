package com.ClinicaDeYmid.contracting_service.application;

import com.ClinicaDeYmid.commons.web.EntityTags;
import com.ClinicaDeYmid.contracting_service.domain.ContractingException;
import com.ClinicaDeYmid.contracting_service.domain.Nit;
import com.ClinicaDeYmid.contracting_service.domain.Payer;
import com.ClinicaDeYmid.contracting_service.domain.PayerIdentity;
import com.ClinicaDeYmid.contracting_service.domain.PayerType;
import com.ClinicaDeYmid.contracting_service.domain.Payers;
import com.ClinicaDeYmid.contracting_service.support.InMemoryPayers;
import com.ClinicaDeYmid.contracting_service.support.PayerFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayerCommandsTest {

    private Payers payers;
    private PayerCommands commands;

    @BeforeEach
    void setUp() {
        payers = new InMemoryPayers();
        commands = new PayerCommands(payers, TransactionOperations.withoutTransaction(), PayerFixtures.CLOCK);
    }

    @Test
    void registersOnlyOnePayerPerNit() {
        commands.register(PayerFixtures.registration());

        assertThatThrownBy(() -> commands.register(PayerFixtures.registration()))
                .isInstanceOf(ContractingException.NitAlreadyRegistered.class);
    }

    @Test
    void rejectsChangesMadeOverAnOutdatedVersion() {
        Payer payer = commands.register(PayerFixtures.registration());

        assertThatThrownBy(() -> commands.suspend(payer.uuid(), payer.version() + 1, "Cartera vencida sin acuerdo"))
                .isInstanceOf(EntityTags.StaleVersion.class);
    }

    @Test
    void refusesToMoveANitToAPayerThatAlreadyHasIt() {
        Payer first = commands.register(PayerFixtures.registration("901234567"));
        commands.register(PayerFixtures.registration("890903938"));

        assertThatThrownBy(() -> commands.correctIdentity(first.uuid(), first.version(),
                new PayerIdentity("Otra EPS", new Nit("890903938"), PayerType.EPS, null)))
                .isInstanceOf(ContractingException.NitAlreadyRegistered.class);
    }

    @Test
    void keepsTheSameNitWhenCorrectingTheRestOfTheIdentity() {
        Payer payer = commands.register(PayerFixtures.registration());

        Payer corrected = commands.correctIdentity(payer.uuid(), payer.version(),
                new PayerIdentity("Salud Total EPS-S S.A.", new Nit("901234567"), PayerType.EPS, "EPS002"));

        assertThat(corrected.socialReason()).isEqualTo("Salud Total EPS-S S.A.");
    }

    @Test
    void answersNotFoundForAnUnknownPayer() {
        assertThatThrownBy(() -> commands.suspend(UUID.randomUUID(), 0, "Cartera vencida sin acuerdo"))
                .isInstanceOf(ContractingException.PayerNotFound.class);
    }
}
