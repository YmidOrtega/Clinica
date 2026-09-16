package com.ClinicaDeYmid.contracting_service.infrastructure.persistence;

import com.ClinicaDeYmid.contracting_service.application.PayerHistory;
import com.ClinicaDeYmid.contracting_service.domain.ContactInfo;
import com.ClinicaDeYmid.contracting_service.domain.Nit;
import com.ClinicaDeYmid.contracting_service.domain.Payer;
import com.ClinicaDeYmid.contracting_service.domain.PayerIdentity;
import com.ClinicaDeYmid.contracting_service.domain.PayerStatus;
import com.ClinicaDeYmid.contracting_service.domain.PayerType;
import com.ClinicaDeYmid.contracting_service.infrastructure.config.ClockConfiguration;
import com.ClinicaDeYmid.contracting_service.infrastructure.config.PersistenceConfiguration;
import com.ClinicaDeYmid.contracting_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.contracting_service.support.PayerFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaPayers.class, EnversPayerHistory.class, PersistenceConfiguration.class, ClockConfiguration.class, MySqlTestContainer.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PayerPersistenceIT {

    @Autowired
    private JpaPayers payers;

    @Autowired
    private EnversPayerHistory history;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM contracting_history.payers_aud");
        jdbc.update("DELETE FROM contracting_history.revisions");
        jdbc.update("DELETE FROM payers");
    }

    @Test
    void persistsEveryValueObjectOfThePayer() {
        Payer saved = payers.save(PayerFixtures.active());

        Payer found = payers.findByUuid(saved.uuid()).orElseThrow();

        assertThat(found.nit()).isEqualTo(new Nit("901234567"));
        assertThat(found.nit().verificationDigit()).isEqualTo(7);
        assertThat(found.type()).isEqualTo(PayerType.EPS);
        assertThat(found.adresCode()).isEqualTo("EPS002");
        assertThat(found.contact().billingEmail()).isEqualTo("radicacion@saludtotal.test");
        assertThat(found.status()).isEqualTo(new PayerStatus.Active());
        assertThat(found.createdAt()).isNotNull();
    }

    @Test
    void findsByNitAndKeepsItUnique() {
        payers.save(PayerFixtures.active());

        assertThat(payers.findByNit(new Nit("901.234.567-7"))).isPresent();
        assertThat(payers.existsByNit(new Nit("890903938"))).isFalse();
        assertThatThrownBy(() -> payers.save(PayerFixtures.active()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesToOverwriteAConcurrentChange() {
        Payer saved = payers.save(PayerFixtures.active());
        Payer first = payers.findByUuid(saved.uuid()).orElseThrow();
        Payer second = payers.findByUuid(saved.uuid()).orElseThrow();

        first.suspend("Suspensión por cartera vencida", PayerFixtures.CLOCK);
        payers.save(first);
        second.updateContact(new ContactInfo("Calle 9 # 2-30", "6012223344", null));

        assertThatThrownBy(() -> payers.save(second)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void keepsEveryChangeInTheHistory() {
        Payer saved = payers.save(PayerFixtures.active());
        Payer stored = payers.findByUuid(saved.uuid()).orElseThrow();
        stored.correctIdentity(new PayerIdentity("Salud Total EPS-S S.A.", new Nit("901234567"), PayerType.EPS, "EPS002"));
        Payer corrected = payers.save(stored);
        corrected.suspend("Suspensión por cartera vencida", PayerFixtures.CLOCK);
        payers.save(corrected);

        List<PayerHistory.Revision> revisions = history.of(saved.uuid());

        assertThat(revisions).hasSize(3);
        assertThat(revisions.get(0).changeType()).isEqualTo(PayerHistory.ChangeType.CREATED);
        assertThat(revisions.get(1).state().socialReason()).isEqualTo("Salud Total EPS-S S.A.");
        assertThat(revisions.get(2).state().status().code()).isEqualTo(PayerStatus.Code.SUSPENDED);
        assertThat(revisions).allSatisfy(revision -> assertThat(revision.revisedAt()).isNotNull());
    }

    @Test
    void searchesByThePrefixOfTheSocialReason() {
        payers.save(PayerFixtures.active());
        payers.save(com.ClinicaDeYmid.contracting_service.domain.Payer.register(
                new com.ClinicaDeYmid.contracting_service.domain.PayerRegistration("Sanitas EPS S.A.S.",
                        new Nit("890903938"), PayerType.EPS, null,
                        new ContactInfo("Calle 100 # 11-60", "6014871920", null)), PayerFixtures.CLOCK));

        assertThat(payers.searchBySocialReason("Salud", 0, 10)).hasSize(1);
        assertThat(payers.countBySocialReason("S")).isEqualTo(2);
        assertThat(payers.searchBySocialReason("Zeta", 0, 10)).isEmpty();
    }
}
