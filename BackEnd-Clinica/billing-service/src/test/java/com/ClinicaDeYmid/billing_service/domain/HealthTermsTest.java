package com.ClinicaDeYmid.billing_service.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HealthTermsTest {

    @Test
    void aPayerWithoutAContractIsBilledByEventWithTheReason() {
        HealthTerms terms = HealthTerms.uncontracted(UncontractedCare.EMERGENCY, CoveragePlan.UPC_SUBSIDIZED, null);

        assertThat(terms.modality()).isEqualTo(PaymentModality.EVENT);
        assertThat(terms.cucon()).isNull();
        assertThat(terms.billedWithoutContract()).isTrue();
        assertThat(HealthTerms.privatePatient().billedWithoutContract()).isFalse();
    }

    @Test
    void theParticularCareIsNeverBilledToAPayer() {
        assertThatThrownBy(() -> HealthTerms.uncontracted(UncontractedCare.PRIVATE_PATIENT,
                CoveragePlan.UPC_CONTRIBUTORY, null)).isInstanceOf(BillingException.InvalidData.class);
        assertThatThrownBy(() -> HealthTerms.uncontracted(UncontractedCare.EMERGENCY, CoveragePlan.PRIVATE, null))
                .isInstanceOf(BillingException.InvalidData.class);
    }

    @Test
    void thePolicyGoesOnlyWithSoatAndVoluntaryPlans() {
        assertThat(HealthTerms.uncontracted(UncontractedCare.ADRES_SOAT_OR_VOLUNTARY_PLAN, CoveragePlan.SOAT_POLICY,
                "AT-1234-5678").policyNumber()).isEqualTo("AT-1234-5678");
        assertThatThrownBy(() -> HealthTerms.uncontracted(UncontractedCare.ADRES_SOAT_OR_VOLUNTARY_PLAN,
                CoveragePlan.PREPAID_MEDICINE, " ")).isInstanceOf(BillingException.InvalidData.class);
        assertThatThrownBy(() -> HealthTerms.contracted(PaymentModality.EVENT, CoveragePlan.UPC_CONTRIBUTORY,
                "a".repeat(64), "POL-1")).isInstanceOf(BillingException.InvalidData.class);
        assertThatThrownBy(() -> HealthTerms.uncontracted(UncontractedCare.ADRES_SOAT_OR_VOLUNTARY_PLAN,
                CoveragePlan.HEALTH_POLICY, "póliza con espacios")).isInstanceOf(BillingException.InvalidData.class);
    }
}
