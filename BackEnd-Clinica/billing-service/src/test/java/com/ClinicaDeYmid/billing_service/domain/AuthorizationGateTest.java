package com.ClinicaDeYmid.billing_service.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationGateTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-09-27T15:00:00Z"), BOGOTA);
    private static final LocalDate TODAY = LocalDate.parse("2026-09-27");
    private static final UUID MRI = UUID.randomUUID();

    @Test
    void aServiceThatNeedsAuthorizationBlocksTheSaleWithoutOne() {
        Sale sale = draft();
        SaleLine line = charge(sale, MRI, TODAY, new LineOrigin.Manual());

        PricingTerms terms = terms(line, AuthorizationEvidence.none());

        assertThat(sale.price(terms, BOGOTA).authorizations().get(line.uuid())).isEqualTo(AuthorizationCheck.MISSING);
        assertThatThrownBy(() -> sale.confirm(terms, NOW))
                .isInstanceOf(BillingException.LinesWithoutAuthorization.class)
                .hasMessageContaining("883101");
    }

    @Test
    void anAuthorizationListingTheServiceCoversIt() {
        Sale sale = draft();
        SaleLine line = charge(sale, MRI, TODAY, new LineOrigin.Manual());

        sale.confirm(terms(line, evidence(grant(Set.of(MRI), false, "2026-09-01", "2026-10-31"))), NOW);

        assertThat(sale.status()).isInstanceOf(SaleStatus.Confirmed.class);
    }

    @Test
    void anAuthorizationForEverythingCoversIt() {
        Sale sale = draft();
        SaleLine line = charge(sale, MRI, TODAY, new LineOrigin.Manual());

        assertThat(sale.price(terms(line, evidence(grant(Set.of(), true, null, null))), BOGOTA)
                .authorizations().get(line.uuid())).isEqualTo(AuthorizationCheck.AUTHORIZED);
    }

    @Test
    void theAuthorizationMustBeValidOnTheDayOfTheService() {
        Sale sale = draft();
        SaleLine line = charge(sale, MRI, TODAY, new LineOrigin.Manual());

        assertThat(sale.price(terms(line, evidence(grant(Set.of(MRI), false, "2026-08-01", "2026-08-31"))), BOGOTA)
                .authorizations().get(line.uuid())).isEqualTo(AuthorizationCheck.MISSING);
    }

    @Test
    void aLinePreloadedFromAnAuthorizationThatWasRevokedIsNoLongerCovered() {
        Sale sale = draft();
        UUID revoked = UUID.randomUUID();
        SaleLine line = charge(sale, UUID.randomUUID(), TODAY, new LineOrigin.Authorized(revoked, "AUT-1"));

        assertThat(sale.price(terms(line, AuthorizationEvidence.none()), BOGOTA).authorizations().get(line.uuid()))
                .isEqualTo(AuthorizationCheck.MISSING);
        assertThat(sale.price(terms(line, evidence(new AuthorizationEvidence.Grant(revoked, "AUT-1", null, null,
                Set.of(), false))), BOGOTA).authorizations().get(line.uuid())).isEqualTo(AuthorizationCheck.AUTHORIZED);
    }

    @Test
    void whatWasDoneDuringTheEmergencyIsExemptByLaw() {
        Sale sale = draft();
        SaleLine duringEmergency = charge(sale, MRI, LocalDate.parse("2026-09-25"), new LineOrigin.Manual());
        SaleLine afterwards = charge(sale, MRI, TODAY, new LineOrigin.Manual());
        AuthorizationEvidence evidence = new AuthorizationEvidence(List.of(), List.of(
                new AuthorizationEvidence.EmergencyPeriod(Instant.parse("2026-09-25T13:00:00Z"),
                        Instant.parse("2026-09-26T02:00:00Z"))));
        PricingTerms terms = new PricingTerms(UUID.randomUUID(), "CT-1", UUID.randomUUID(), Map.of(
                duringEmergency.uuid(), tariff(), afterwards.uuid(), tariff()), List.of(),
                Set.of(duringEmergency.uuid(), afterwards.uuid()), evidence);

        PricedSale priced = sale.price(terms, BOGOTA);

        assertThat(priced.authorizations().get(duringEmergency.uuid())).isEqualTo(AuthorizationCheck.EMERGENCY_EXEMPT);
        assertThat(priced.authorizations().get(afterwards.uuid())).isEqualTo(AuthorizationCheck.MISSING);
    }

    @Test
    void servicesThatDoNotNeedAuthorizationPassUntouched() {
        Sale sale = draft();
        SaleLine line = charge(sale, MRI, TODAY, new LineOrigin.Manual());

        sale.confirm(new PricingTerms(UUID.randomUUID(), "CT-1", UUID.randomUUID(), Map.of(line.uuid(), tariff()),
                List.of()), NOW);

        assertThat(sale.status()).isInstanceOf(SaleStatus.Confirmed.class);
    }

    private static PricingTerms terms(SaleLine line, AuthorizationEvidence evidence) {
        return new PricingTerms(UUID.randomUUID(), "CT-1", UUID.randomUUID(), Map.of(line.uuid(), tariff()), List.of(),
                Set.of(line.uuid()), evidence);
    }

    private static LinePrice tariff() {
        return new LinePrice(PriceOrigin.TARIFF_MANUAL, new BigDecimal("350000"), new BigDecimal("350000"), null, null);
    }

    private static AuthorizationEvidence evidence(AuthorizationEvidence.Grant grant) {
        return new AuthorizationEvidence(List.of(grant), List.of());
    }

    private static AuthorizationEvidence.Grant grant(Set<UUID> items, boolean everything, String from, String to) {
        return new AuthorizationEvidence.Grant(UUID.randomUUID(), "AUT-9", from == null ? null : LocalDate.parse(from),
                to == null ? null : LocalDate.parse(to), items, everything);
    }

    private static SaleLine charge(Sale sale, UUID item, LocalDate day, LineOrigin origin) {
        return sale.charge(new ChargedService(item, "883101", null, "Resonancia magnética de cerebro", null), 1, day,
                origin, NOW);
    }

    private static Sale draft() {
        return Sale.open(EpisodeAccount.open(new AdmissionSnapshot(UUID.randomUUID(), "ADM-2026-000123", 1,
                UUID.randomUUID(), AdmissionKind.INPATIENT, AdmissionSnapshot.Status.ACTIVE, UUID.randomUUID(),
                Instant.parse("2026-09-25T13:00:00Z"), null, null)), 1, new SaleType.NonSurgical());
    }
}
