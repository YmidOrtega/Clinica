package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.domain.AccountStatus;
import com.ClinicaDeYmid.billing_service.domain.AdmissionKind;
import com.ClinicaDeYmid.billing_service.domain.AdmissionSnapshot;
import com.ClinicaDeYmid.billing_service.domain.DischargeType;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;

import java.time.Instant;
import java.util.UUID;

final class AccountResponses {

    record StatusView(AccountStatus.Code code, DischargeType discharge, String reason, Instant since) {

        static StatusView from(AccountStatus status) {
            return switch (status) {
                case AccountStatus.Open ignored -> new StatusView(AccountStatus.Code.OPEN, null, null, null);
                case AccountStatus.Frozen frozen ->
                        new StatusView(AccountStatus.Code.FROZEN, frozen.discharge(), null, frozen.since());
                case AccountStatus.Voided voided ->
                        new StatusView(AccountStatus.Code.VOIDED, null, voided.reason(), voided.since());
            };
        }
    }

    record AccountView(UUID uuid, String admissionNumber, UUID admissionUuid, UUID patientUuid, AdmissionKind kind,
                       AdmissionSnapshot.Status admissionStatus, UUID configurationServiceUuid, Instant openedAt,
                       StatusView status) {

        static AccountView from(EpisodeAccount account) {
            return new AccountView(account.uuid(), account.admissionNumber(), account.admissionUuid(),
                    account.patientUuid(), account.kind(), account.admissionStatus(),
                    account.configurationServiceUuid(), account.openedAt(), StatusView.from(account.status()));
        }
    }

    private AccountResponses() {
    }
}
