package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.application.EpisodeAccountProjection;
import com.ClinicaDeYmid.billing_service.domain.AdmissionKind;
import com.ClinicaDeYmid.billing_service.domain.AdmissionSnapshot;
import com.ClinicaDeYmid.billing_service.domain.DischargeType;
import com.ClinicaDeYmid.billing_service.domain.StayType;
import com.ClinicaDeYmid.billing_service.support.AdmissionEvents;
import com.ClinicaDeYmid.billing_service.support.StubbedServices;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StayApiIT extends IntegrationTest {

    private static final String WARD_DAY = "7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d";
    private static final String ICU_DAY = "8b9c0d1e-2f3a-4b4c-9d5e-6f7a8b9c0d1e";

    @Autowired
    private EpisodeAccountProjection projection;

    @Test
    void draftsTheStaySaleWhenThePatientLeaves() throws Exception {
        billStay(StayType.GENERAL_WARD, WARD_DAY, "109101", "Estancia en habitación general");
        billStay(StayType.ICU_ADULT, ICU_DAY, "109501", "Estancia en unidad de cuidado intensivo adulto");
        Instant admitted = Instant.now().minus(Duration.ofDays(4)).truncatedTo(ChronoUnit.SECONDS);
        UUID admission = UUID.randomUUID();
        String number = AdmissionEvents.nextNumber();

        follow(admission, number, 0, AdmissionSnapshot.Status.REGISTERED, admitted, null, null);
        follow(admission, number, 1, AdmissionSnapshot.Status.ACTIVE, admitted.plus(Duration.ofHours(1)),
                UUID.randomUUID(), StayType.GENERAL_WARD);
        follow(admission, number, 2, AdmissionSnapshot.Status.ACTIVE, admitted.plus(Duration.ofHours(27)),
                UUID.randomUUID(), StayType.ICU_ADULT);

        as("BILLING", get("/api/v1/billing/accounts/" + number + "/stay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segments.length()").value(2))
                .andExpect(jsonPath("$.periods[0].stayType").value("GENERAL_WARD"))
                .andExpect(jsonPath("$.periods[1].stayType").value("ICU_ADULT"))
                .andExpect(jsonPath("$.periods[1].billable").value(true));

        follow(admission, number, 3, AdmissionSnapshot.Status.DISCHARGED, admitted.plus(Duration.ofHours(61)), null, null);
        follow(admission, number, 3, AdmissionSnapshot.Status.DISCHARGED, admitted.plus(Duration.ofHours(61)), null, null);

        as("BILLING", get("/api/v1/billing/accounts/" + number + "/sales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].origin").value("STAY"))
                .andExpect(jsonPath("$[0].status.code").value("DRAFT"))
                .andExpect(jsonPath("$[0].lines.length()").value(2))
                .andExpect(jsonPath("$[0].lines[0].cupsCode").value("109101"))
                .andExpect(jsonPath("$[0].lines[0].quantity").value(1))
                .andExpect(jsonPath("$[0].lines[0].origin.code").value("STAY"))
                .andExpect(jsonPath("$[0].lines[1].cupsCode").value("109501"))
                .andExpect(jsonPath("$[0].lines[1].quantity").value(2));
        as("BILLING", get("/api/v1/billing/accounts/" + number + "/stay"))
                .andExpect(jsonPath("$.totalPeriods").value(3));
    }

    @Test
    void aStayTypeWithoutServiceIsReportedInsteadOfBilledSilently() throws Exception {
        Instant admitted = Instant.now().minus(Duration.ofDays(2)).truncatedTo(ChronoUnit.SECONDS);
        UUID admission = UUID.randomUUID();
        String number = AdmissionEvents.nextNumber();

        follow(admission, number, 1, AdmissionSnapshot.Status.ACTIVE, admitted, UUID.randomUUID(), StayType.ICU_NEONATAL);
        follow(admission, number, 2, AdmissionSnapshot.Status.DISCHARGED, admitted.plus(Duration.ofHours(10)), null, null);

        as("BILLING", get("/api/v1/billing/accounts/" + number + "/stay"))
                .andExpect(jsonPath("$.unbillableTypes[0]").value("ICU_NEONATAL"))
                .andExpect(jsonPath("$.periods[0].billable").value(false));
        as("BILLING", get("/api/v1/billing/accounts/" + number + "/sales")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void onlyConfigurationDecidesHowAStayIsBilled() throws Exception {
        portfolio(WARD_DAY, "109101", "Estancia en habitación general");

        change("BILLING", put("/api/v1/billing/stay-charges/PRIVATE_ROOM"), 0,
                "{\"portfolioItemUuid\":\"" + WARD_DAY + "\"}").andExpect(status().isOk());
        as("RECEPTIONIST", put("/api/v1/billing/stay-charges/PRIVATE_ROOM"),
                "{\"portfolioItemUuid\":\"" + WARD_DAY + "\"}").andExpect(status().isForbidden());
        as("BILLING", get("/api/v1/billing/stay-charges"))
                .andExpect(jsonPath("$[?(@.stayType == 'PRIVATE_ROOM')].cupsCode").value("109101"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stay_charges WHERE stay_type = 'PRIVATE_ROOM'",
                Integer.class)).isEqualTo(1);
    }

    private void billStay(StayType type, String item, String cups, String name) throws Exception {
        portfolio(item, cups, name);
        change("BILLING", put("/api/v1/billing/stay-charges/" + type), 0, "{\"portfolioItemUuid\":\"" + item + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cupsCode").value(cups));
    }

    private void follow(UUID admission, String number, long version, AdmissionSnapshot.Status status, Instant at,
                        UUID bed, StayType type) {
        projection.follow(new AdmissionSnapshot(admission, number, version, UUID.randomUUID(), AdmissionKind.INPATIENT,
                status, UUID.randomUUID(), at, status == AdmissionSnapshot.Status.DISCHARGED ? DischargeType.MEDICAL : null,
                null, bed, type));
    }

    private static void portfolio(String uuid, String cups, String name) {
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/portfolio-items/" + uuid)).willReturn(okJson("""
                {"uuid":"%s","cupsCode":"%s","name":"%s","category":"HOSPITALIZATION",
                 "status":{"code":"ACTIVE","offered":true}}""".formatted(uuid, cups, name))));
    }
}
