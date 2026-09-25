package com.ClinicaDeYmid.billing_service.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PractitionerFeeTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-09-27T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final TeamMember SURGEON = new TeamMember(SurgicalRole.SURGEON, UUID.randomUUID(), "Ana Cirujana", "RM-1");
    private static final TeamMember ANESTHESIOLOGIST =
            new TeamMember(SurgicalRole.ANESTHESIOLOGIST, UUID.randomUUID(), "Luis Anestesia", "RM-2");
    private static final TeamMember ASSISTANT = new TeamMember(SurgicalRole.ASSISTANT, UUID.randomUUID(), "Eva Ayuda", "RM-3");

    @Test
    void eachFeePaidByTheLiquidationBecomesAFeeOwedToWhoeverHeldTheRole() {
        Sale sale = confirmed();

        List<PractitionerFee> fees = PractitionerFee.owedFor(sale, Map.of(
                SURGEON.practitionerUuid(), Optional.of(perProcedure("514201", "900000")),
                ANESTHESIOLOGIST.practitionerUuid(), Optional.of(new FeeAgreementTerms(UUID.randomUUID(), "HOURLY", Map.of())),
                ASSISTANT.practitionerUuid(), Optional.empty()));

        assertThat(fees).extracting(PractitionerFee::role)
                .containsExactlyInAnyOrder(SurgicalRole.SURGEON, SurgicalRole.ANESTHESIOLOGIST);
        PractitionerFee surgeon = fees.stream().filter(fee -> fee.role() == SurgicalRole.SURGEON).findFirst().orElseThrow();
        assertThat(surgeon.status()).isEqualTo(PractitionerFee.Status.PAYABLE);
        assertThat(surgeon.amount()).isEqualByComparingTo("900000");
        PractitionerFee anesthesia = fees.stream().filter(fee -> fee.role() == SurgicalRole.ANESTHESIOLOGIST).findFirst().orElseThrow();
        assertThat(anesthesia.status()).isEqualTo(PractitionerFee.Status.UNAGREED);
        assertThat(anesthesia.amount()).isNull();
        assertThat(anesthesia.statusReason()).contains("HOURLY");
    }

    @Test
    void aProcedureMissingFromTheAgreementIsLeftForReview() {
        List<PractitionerFee> fees = PractitionerFee.owedFor(confirmed(), Map.of(
                SURGEON.practitionerUuid(), Optional.of(perProcedure("530101", "500000"))));

        assertThat(fees).filteredOn(fee -> fee.role() == SurgicalRole.SURGEON).singleElement()
                .satisfies(fee -> {
                    assertThat(fee.status()).isEqualTo(PractitionerFee.Status.UNAGREED);
                    assertThat(fee.statusReason()).contains("514201");
                });
    }

    @Test
    void aNonSurgicalSaleOwesNoFees() {
        Sale sale = Sale.open(account(), 1, new SaleType.NonSurgical());

        assertThat(PractitionerFee.owedFor(sale, Map.of())).isEmpty();
    }

    @Test
    void voidingIsFinalAndKeepsTheReason() {
        PractitionerFee fee = PractitionerFee.owedFor(confirmed(), Map.of()).getFirst();

        fee.voidBecause("La venta fue anulada");
        fee.voidBecause("Otra vez");

        assertThat(fee.status()).isEqualTo(PractitionerFee.Status.VOIDED);
        assertThat(fee.statusReason()).isEqualTo("La venta fue anulada");
    }

    private static FeeAgreementTerms perProcedure(String cups, String amount) {
        return new FeeAgreementTerms(UUID.randomUUID(), FeeAgreementTerms.PER_PROCEDURE, Map.of(cups, new BigDecimal(amount)));
    }

    private static Sale confirmed() {
        Sale sale = Sale.open(account(), 1, new SaleType.Surgical(LocalDate.parse("2026-09-26")));
        SaleLine line = sale.chargeProcedure(new ChargedService(UUID.randomUUID(), "514201", null, "Colecistectomía", null),
                "ABDOMINAL", new LineOrigin.Manual(), NOW);
        sale.assignTeam(List.of(SURGEON, ANESTHESIOLOGIST, ASSISTANT), NOW);
        SurgicalDetail detail = new SurgicalDetail(1, true, false, new BigDecimal("110"), List.of(
                charge(SurgicalComponent.SURGEON, "139700"), charge(SurgicalComponent.ANESTHESIOLOGIST, "105600"),
                charge(SurgicalComponent.ASSISTANT, "0"), charge(SurgicalComponent.OPERATING_ROOM, "150000")));
        sale.confirm(new PricingTerms(UUID.randomUUID(), "CT", UUID.randomUUID(), Map.of(line.uuid(),
                new LinePrice(PriceOrigin.SURGICAL_LIQUIDATION, detail.total(), detail.total(), null, "ISS", detail)),
                List.of()), NOW);
        return sale;
    }

    private static ComponentCharge charge(SurgicalComponent component, String amount) {
        return new ComponentCharge(component, new BigDecimal(amount), new BigDecimal("100"), new BigDecimal(amount));
    }

    private static EpisodeAccount account() {
        return EpisodeAccount.open(new AdmissionSnapshot(UUID.randomUUID(), "ADM-2026-000123", 1, UUID.randomUUID(),
                AdmissionKind.INPATIENT, AdmissionSnapshot.Status.ACTIVE, UUID.randomUUID(),
                Instant.parse("2026-09-25T13:00:00Z"), null, null));
    }
}
