package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.application.EpisodeAccountProjection;
import com.ClinicaDeYmid.billing_service.domain.AdmissionKind;
import com.ClinicaDeYmid.billing_service.domain.AdmissionSnapshot;
import com.ClinicaDeYmid.billing_service.domain.DischargeType;
import com.ClinicaDeYmid.billing_service.support.AdmissionEvents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountApiIT extends IntegrationTest {

    private static final String ACCOUNTS = "/api/v1/billing/accounts";

    @Autowired
    private EpisodeAccountProjection projection;

    @Test
    void showsTheAccountOfAnEpisodeByItsAdmissionNumber() throws Exception {
        String number = AdmissionEvents.nextNumber();
        UUID admission = UUID.randomUUID();
        projection.follow(snapshot(admission, number, 0, AdmissionSnapshot.Status.REGISTERED, null,
                Instant.parse("2026-09-25T13:00:00Z")));

        as("BILLING", get(ACCOUNTS + "/" + number))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admissionUuid").value(admission.toString()))
                .andExpect(jsonPath("$.kind").value("EMERGENCY"))
                .andExpect(jsonPath("$.status.code").value("OPEN"))
                .andExpect(jsonPath("$.openedAt").value("2026-09-25T13:00:00Z"));
    }

    @Test
    void listsTheDischargedEpisodesWaitingForTheirInvoiceOldestFirst() throws Exception {
        String older = frozen(Instant.parse("2020-01-01T10:00:00Z"));
        String newer = frozen(Instant.parse("2020-01-02T10:00:00Z"));

        String body = as("BILLING", get(ACCOUNTS).param("status", "FROZEN").param("limit", "200"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body.indexOf(older)).isNotNegative().isLessThan(body.indexOf(newer));
    }

    @Test
    void recordsTheFullHistoryOfTheAccount() {
        String number = AdmissionEvents.nextNumber();
        UUID admission = UUID.randomUUID();
        projection.follow(snapshot(admission, number, 0, AdmissionSnapshot.Status.REGISTERED, null,
                Instant.parse("2026-09-25T13:00:00Z")));
        projection.follow(snapshot(admission, number, 3, AdmissionSnapshot.Status.DISCHARGED, DischargeType.MEDICAL,
                Instant.parse("2026-09-26T13:00:00Z")));

        assertThat(jdbc.queryForList("""
                SELECT a.status FROM billing_history.episode_accounts_aud a
                WHERE a.admission_number = ? ORDER BY a.rev""", String.class, number))
                .containsExactly("OPEN", "FROZEN");
    }

    @Test
    void answersNotFoundForAnEpisodeAdmissionsNeverAnnounced() throws Exception {
        as("BILLING", get(ACCOUNTS + "/ADM-2026-999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        as("BILLING", get(ACCOUNTS + "/not-a-number")).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "DOCTOR", "NURSE"})
    void onlyBillingReadsTheAccounts(String role) throws Exception {
        as(role, get(ACCOUNTS).param("status", "OPEN")).andExpect(status().isForbidden());
    }

    private String frozen(Instant dischargedAt) {
        String number = AdmissionEvents.nextNumber();
        projection.follow(snapshot(UUID.randomUUID(), number, 2, AdmissionSnapshot.Status.DISCHARGED,
                DischargeType.MEDICAL, dischargedAt));
        return number;
    }

    private static AdmissionSnapshot snapshot(UUID admission, String number, long version,
                                              AdmissionSnapshot.Status status, DischargeType discharge, Instant at) {
        return new AdmissionSnapshot(admission, number, version, UUID.randomUUID(), AdmissionKind.EMERGENCY, status,
                UUID.randomUUID(), at, discharge, null);
    }
}
