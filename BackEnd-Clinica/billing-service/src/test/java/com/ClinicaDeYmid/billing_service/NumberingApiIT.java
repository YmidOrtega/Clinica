package com.ClinicaDeYmid.billing_service;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.ClinicaDeYmid.billing_service.support.BillingSetup.ISSUER;
import static com.ClinicaDeYmid.billing_service.support.BillingSetup.RESOLUTIONS;
import static com.ClinicaDeYmid.billing_service.support.BillingSetup.configuration;
import static com.ClinicaDeYmid.billing_service.support.BillingSetup.resolution;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NumberingApiIT extends IntegrationTest {

    @BeforeEach
    void startWithAnIssuerAndNoResolutions() throws Exception {
        forgetTheBillingSetup();
        as("ADMIN", post(ISSUER), configuration()).andExpect(status().isCreated());
    }

    @Test
    void registersAResolutionPendingWithItsWholeRangeAhead() throws Exception {
        as("ADMIN", post(RESOLUTIONS), resolution("18760000001", "setp", 990000000, 995000000))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.prefix").value("SETP"))
                .andExpect(jsonPath("$.environment").value("TEST"))
                .andExpect(jsonPath("$.status.code").value("PENDING"))
                .andExpect(jsonPath("$.nextNumber").value(990000000))
                .andExpect(jsonPath("$.remaining").value(5000001))
                .andExpect(jsonPath("$.technicalKeyEnding").value("162c"))
                .andExpect(jsonPath("$.technicalKey").doesNotExist());
    }

    @Test
    void activatingAResolutionRetiresTheOneInUse() throws Exception {
        String first = registered("18760000001", "SETP", 1, 1000);
        String second = registered("18760000002", "SETP", 1001, 2000);

        change("ADMIN", post(RESOLUTIONS + "/" + first + "/activation"), 0, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("ACTIVE"));
        change("ADMIN", post(RESOLUTIONS + "/" + second + "/activation"), 0, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("ACTIVE"));

        as("BILLING", get(RESOLUTIONS + "/" + first))
                .andExpect(jsonPath("$.status.code").value("RETIRED"))
                .andExpect(jsonPath("$.status.reason").value("Reemplazada por la resolución 18760000002"));
    }

    @Test
    void refusesARangeThatCrossesAnotherOfTheSamePrefix() throws Exception {
        registered("18760000001", "SETP", 1, 1000);

        as("ADMIN", post(RESOLUTIONS), resolution("18760000002", "SETP", 1000, 2000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOLUTION_RANGE_OVERLAPS"));
        as("ADMIN", post(RESOLUTIONS), resolution("18760000002", "FV", 1, 1000))
                .andExpect(status().isCreated());
        as("ADMIN", post(RESOLUTIONS), resolution("18760000001", "SETP", 5000, 6000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOLUTION_ALREADY_REGISTERED"));
    }

    @Test
    void refusesToActivateAResolutionThatIsNotValidToday() throws Exception {
        as("ADMIN", post(RESOLUTIONS), resolution("18760000003", "FV", 1, 100, "2026-01-01", "2026-03-31"))
                .andExpect(status().isCreated());
        String expired = jdbc.queryForObject(
                "SELECT uuid FROM numbering_resolutions WHERE resolution_number = '18760000003'", String.class);

        change("ADMIN", post(RESOLUTIONS + "/" + expired + "/activation"), 0, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RESOLUTION_OUTSIDE_VALIDITY"));
    }

    @Test
    void retiresAResolutionWithAReasonAndKeepsItRetired() throws Exception {
        String resolution = registered("18760000001", "SETP", 1, 1000);

        change("ADMIN", post(RESOLUTIONS + "/" + resolution + "/retirement"), 0, "{\"reason\":\"Error de digitación\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("RETIRED"))
                .andExpect(jsonPath("$.status.reason").value("Error de digitación"));

        change("ADMIN", post(RESOLUTIONS + "/" + resolution + "/activation"), 1, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RESOLUTION_INVALID_TRANSITION"));
    }

    @Test
    void theDatabaseKeepsASingleActiveResolution() throws Exception {
        String first = registered("18760000001", "SETP", 1, 1000);
        registered("18760000002", "SETP", 1001, 2000);
        change("ADMIN", post(RESOLUTIONS + "/" + first + "/activation"), 0, null).andExpect(status().isOk());

        assertThatThrownBy(() -> jdbc.update("""
                UPDATE numbering_resolutions SET status = 'ACTIVE', status_changed_at = NOW(6)
                WHERE resolution_number = '18760000002'"""))
                .hasMessageContaining("uk_numbering_resolutions_single_active");
    }

    @Test
    void readersSeeTheResolutionsButOnlyConfigurationChangesThem() throws Exception {
        String resolution = registered("18760000001", "SETP", 1, 1000);

        as("BILLING", get(RESOLUTIONS)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        as("RECEPTIONIST", get(RESOLUTIONS)).andExpect(status().isForbidden());
        change("RECEPTIONIST", post(RESOLUTIONS + "/" + resolution + "/activation"), 0, null)
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT status FROM numbering_resolutions", String.class)).isEqualTo("PENDING");
    }

    private String registered(String number, String prefix, long from, long to) throws Exception {
        String body = as("ADMIN", post(RESOLUTIONS), resolution(number, prefix, from, to))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.uuid");
    }
}
