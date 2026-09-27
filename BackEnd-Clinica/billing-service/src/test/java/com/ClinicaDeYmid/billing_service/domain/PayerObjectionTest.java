package com.ClinicaDeYmid.billing_service.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayerObjectionTest {

    private static final String CUV = "a2cf8c6b2f9563d39c5cd7dd8d73e77a5926165a269c07ee84a1c4162ecd198526501a93c1b2f8155ddaf88c82fb15b4";
    private static final Function<String, Optional<ObjectionCode>> CATALOG = code -> switch (code) {
        case "TA0201" -> Optional.of(new ObjectionCode(code, ObjectionCode.Kind.GLOSS, "TA", "TA02", true, "Tarifa"));
        case "TA02" -> Optional.of(new ObjectionCode(code, ObjectionCode.Kind.GLOSS, "TA", "TA02", false, "Grupo"));
        case "SA5601" -> Optional.of(new ObjectionCode(code, ObjectionCode.Kind.GLOSS, "SA", "SA56", true, "Indicadores"));
        case "DE1601" -> Optional.of(new ObjectionCode(code, ObjectionCode.Kind.DEVOLUTION, "DE", "DE16", true, "Otro"));
        default -> Optional.empty();
    };

    @Test
    void answersEachGlossWithAResponseThatMatchesTheAcceptedValue() {
        InvoiceFiling filing = filing();
        LocalDate today = filing.filedOn();
        PayerObjection gloss = PayerObjection.register(filing, PayerObjection.Kind.GLOSS, "GL-1", today,
                List.of(new PayerObjection.Item(1, "TA0201", new BigDecimal("8000"), "Tarifa")), List.of(), CATALOG,
                today);

        assertThat(gloss.responseDeadline()).isEqualTo(BusinessCalendar.plusBusinessDays(today, 15));
        assertThat(gloss.extemporaneous()).isFalse();
        assertThatThrownBy(() -> gloss.respond("R-1", today, List.of(answer("RE9702", "7000")), today))
                .isInstanceOf(BillingException.InvalidData.class);
        assertThatThrownBy(() -> gloss.respond("R-1", today, List.of(answer("RE9801", "8000")), today))
                .isInstanceOf(BillingException.InvalidData.class);
        assertThatThrownBy(() -> gloss.respond("R-1", today, List.of(answer("RE9701", "8000")), today))
                .isInstanceOf(BillingException.InvalidData.class);

        gloss.respond("R-1", today, List.of(answer("RE9801", "3000")), today);

        assertThat(gloss.acceptedAmount()).isEqualByComparingTo("3000");
        assertThat(gloss.acceptedByLine(Map.of(1, new BigDecimal("8000")))).containsExactly(new CreditRequest(1, null, new BigDecimal("3000.00")));
        assertThatThrownBy(() -> gloss.decide(today, List.of(new PayerObjection.Ruling(1, new BigDecimal("5001"))),
                today)).isInstanceOf(BillingException.InvalidData.class);
        gloss.decide(today, List.of(new PayerObjection.Ruling(1, new BigDecimal("5000"))), today);
        assertThat(gloss.outcome()).isEqualTo(PayerObjection.Outcome.UPHELD);
        assertThatThrownBy(() -> gloss.respond("R-2", today, List.of(answer("RE9602", "0")), today))
                .isInstanceOf(BillingException.InvalidObjection.class);
    }

    @Test
    void aDevolutionTakesTheWholeBalanceAndOnlyApplicableCodesOfItsKind() {
        InvoiceFiling filing = filing();
        LocalDate today = filing.filedOn();
        PayerObjection devolution = PayerObjection.register(filing, PayerObjection.Kind.DEVOLUTION, "DV-1", today,
                List.of(new PayerObjection.Item(null, "DE1601", null, null)), List.of(), CATALOG, today);

        assertThat(devolution.claimedAmount()).isEqualByComparingTo(filing.invoice().payableTotal());
        assertThatThrownBy(() -> PayerObjection.register(filing, PayerObjection.Kind.DEVOLUTION, "DV-2", today,
                List.of(new PayerObjection.Item(null, "DE1601", null, null)), List.of(devolution), CATALOG, today))
                .isInstanceOf(BillingException.InvalidObjection.class);
        assertThatThrownBy(() -> PayerObjection.register(filing, PayerObjection.Kind.GLOSS, "GL-1", today,
                List.of(new PayerObjection.Item(1, "DE1601", BigDecimal.TEN, null)), List.of(), CATALOG, today))
                .isInstanceOf(BillingException.InvalidData.class);
        assertThatThrownBy(() -> PayerObjection.register(filing, PayerObjection.Kind.GLOSS, "GL-1", today,
                List.of(new PayerObjection.Item(1, "TA02", BigDecimal.TEN, null)), List.of(), CATALOG, today))
                .isInstanceOf(BillingException.InvalidData.class);
        assertThatThrownBy(() -> PayerObjection.register(null, PayerObjection.Kind.GLOSS, "GL-1", today,
                List.of(), List.of(), CATALOG, today)).isInstanceOf(BillingException.InvoiceNotFiled.class);
    }

    @Test
    void anAgreementFollowUpGlossNeedsNoLineAndItsAcceptedValueIsProratedOverWhatIsLeftToCredit() {
        InvoiceFiling filing = filing();
        LocalDate today = filing.filedOn();
        PayerObjection gloss = PayerObjection.register(filing, PayerObjection.Kind.GLOSS, "GL-SA", today,
                List.of(new PayerObjection.Item(null, "SA5601", new BigDecimal("1000"), "Metas de calidad")),
                List.of(), CATALOG, today);

        assertThat(gloss.items().getFirst().invoiceLinePosition()).isNull();
        gloss.respond("R-SA", today, List.of(answer("RE9801", "1.00")), today);

        Map<Integer, BigDecimal> creditable = new LinkedHashMap<>();
        creditable.put(1, new BigDecimal("1.00"));
        creditable.put(2, new BigDecimal("1.00"));
        creditable.put(3, new BigDecimal("1.00"));
        assertThat(gloss.acceptedByLine(creditable)).containsExactly(
                new CreditRequest(1, null, new BigDecimal("0.34")),
                new CreditRequest(2, null, new BigDecimal("0.33")),
                new CreditRequest(3, null, new BigDecimal("0.33")));
        assertThatThrownBy(() -> gloss.acceptedByLine(Map.of(1, new BigDecimal("0.99"))))
                .isInstanceOf(BillingException.CreditExceedsInvoice.class);
    }

    @Test
    void onlyAgreementFollowUpGlossesMayLeaveTheLineOut() {
        InvoiceFiling filing = filing();
        LocalDate today = filing.filedOn();

        assertThatThrownBy(() -> PayerObjection.register(filing, PayerObjection.Kind.GLOSS, "GL-1", today,
                List.of(new PayerObjection.Item(null, "TA0201", BigDecimal.TEN, null)), List.of(), CATALOG, today))
                .isInstanceOf(BillingException.InvalidData.class);
        assertThatThrownBy(() -> PayerObjection.register(filing, PayerObjection.Kind.GLOSS, "GL-1", today,
                List.of(new PayerObjection.Item(null, "SA5601",
                        filing.invoice().payableTotal().add(BigDecimal.ONE), null)), List.of(), CATALOG, today))
                .isInstanceOf(BillingException.InvalidData.class);
    }

    private static PayerObjection.Answer answer(String code, String accepted) {
        return new PayerObjection.Answer(1, code, new BigDecimal(accepted), null);
    }

    private static InvoiceFiling filing() {
        Invoice invoice = InvoiceTest.issuedToThePayer(990000001);
        RipsSubmission validated = RipsSubmission.prepare(invoice, 1, "{}");
        validated.validated(1L, CUV, Instant.now(), false, List.of(), "{}", Instant.now());
        return InvoiceFiling.register(validated, "RAD-1", invoice.issuedOn(), invoice.issuedOn(), invoice.issuedOn());
    }
}
