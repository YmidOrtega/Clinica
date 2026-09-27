package com.ClinicaDeYmid.billing_service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.ClinicaDeYmid.billing_service.support.BillingSetup.ISSUER;
import static com.ClinicaDeYmid.billing_service.support.BillingSetup.RESOLUTIONS;
import static com.ClinicaDeYmid.billing_service.support.BillingSetup.configuration;
import static com.ClinicaDeYmid.billing_service.support.BillingSetup.profile;
import static com.ClinicaDeYmid.billing_service.support.BillingSetup.resolution;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IssuerApiIT extends IntegrationTest {

    @BeforeEach
    void startWithoutAnIssuer() {
        forgetTheBillingSetup();
    }

    @Test
    void registersTheClinicAsIssuerInTheTestEnvironment() throws Exception {
        as("ADMIN", post(ISSUER), configuration())
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.nit").value("800197268"))
                .andExpect(jsonPath("$.verificationDigit").value(4))
                .andExpect(jsonPath("$.departmentCode").value("05"))
                .andExpect(jsonPath("$.taxResponsibilities[0].dianCode").value("O-13"))
                .andExpect(jsonPath("$.taxResponsibilities[1].dianCode").value("O-15"))
                .andExpect(jsonPath("$.environment").value("TEST"));

        as("BILLING", get(ISSUER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalName").value("Clínica de Ymid S.A.S."));
        assertThat(jdbc.queryForObject("SELECT tax_responsibilities FROM issuer", String.class)).isEqualTo("O-13;O-15");
    }

    @Test
    void thereIsOnlyOneIssuer() throws Exception {
        as("ADMIN", post(ISSUER), configuration()).andExpect(status().isCreated());

        as("ADMIN", post(ISSUER), configuration("860034313", 7))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ISSUER_ALREADY_CONFIGURED"));
    }

    @Test
    void refusesANitWhoseVerificationDigitIsWrong() throws Exception {
        as("ADMIN", post(ISSUER), configuration("800197268", 5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NIT_WRONG_VERIFICATION_DIGIT"));
    }

    @Test
    void answersNotFoundUntilTheIssuerIsRegistered() throws Exception {
        as("BILLING", get(ISSUER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ISSUER_NOT_CONFIGURED"));
    }

    @Test
    void correctsTheProfileWithTheVersionItWasReadAt() throws Exception {
        as("ADMIN", post(ISSUER), configuration()).andExpect(status().isCreated());

        change("ADMIN", put(ISSUER), 0, profile("Clínica de Ymid S.A.S. BIC"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.legalName").value("Clínica de Ymid S.A.S. BIC"))
                .andExpect(jsonPath("$.nit").value("800197268"));

        change("ADMIN", put(ISSUER), 0, profile("Otro nombre"))
                .andExpect(status().isPreconditionFailed());
        as("ADMIN", put(ISSUER), profile("Otro nombre"))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void goingToProductionIsFinalAndRetiresTheTestResolutions() throws Exception {
        as("ADMIN", post(ISSUER), configuration()).andExpect(status().isCreated());
        as("ADMIN", post(RESOLUTIONS), resolution("18760000001", "SETP", 990000000, 995000000))
                .andExpect(status().isCreated());

        change("ADMIN", post(ISSUER + "/production"), 0, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").value("PRODUCTION"))
                .andExpect(jsonPath("$.productionSince").isNotEmpty());

        as("BILLING", get(RESOLUTIONS))
                .andExpect(jsonPath("$[0].status.code").value("RETIRED"))
                .andExpect(jsonPath("$[0].status.reason").value("El emisor pasó a facturar en producción"));

        change("ADMIN", post(ISSUER + "/production"), 1, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ISSUER_ALREADY_IN_PRODUCTION"));
    }

    @Test
    void recordsWhoChangedTheIssuer() throws Exception {
        as("SUPER_ADMIN", post(ISSUER), configuration()).andExpect(status().isCreated());
        change("ADMIN", put(ISSUER), 0, profile("Clínica de Ymid S.A.S. BIC")).andExpect(status().isOk());

        assertThat(jdbc.queryForList("""
                SELECT r.revised_by FROM billing_history.issuer_aud a
                JOIN billing_history.revisions r ON r.id = a.rev
                WHERE a.uuid = (SELECT uuid FROM issuer) ORDER BY a.rev""", String.class))
                .containsExactly("00000000-0000-4000-8000-000000000001", "00000000-0000-4000-8000-000000000002");
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "DOCTOR", "NURSE"})
    void onlyBillingAndAdministrationSeeOrChangeTheIssuer(String role) throws Exception {
        as(role, get(ISSUER)).andExpect(status().isForbidden());
        as(role, post(ISSUER), configuration()).andExpect(status().isForbidden());
    }
}
