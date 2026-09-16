package com.ClinicaDeYmid.contracting_service.infrastructure.web;

import com.ClinicaDeYmid.contracting_service.application.PayerHistory;
import com.ClinicaDeYmid.contracting_service.domain.Payer;
import com.ClinicaDeYmid.contracting_service.domain.PayerStatus;
import com.ClinicaDeYmid.contracting_service.domain.PayerType;

import java.time.Instant;
import java.util.UUID;

final class PayerResponses {

    private PayerResponses() {
    }

    record PayerView(UUID uuid, long version, String socialReason, String nit, PayerType type, String typeLabel,
                     String adresCode, ContactView contact, StatusView status, Instant createdAt, Instant updatedAt) {

        static PayerView from(Payer payer) {
            return new PayerView(payer.uuid(), payer.version(), payer.socialReason(), payer.nit().formatted(),
                    payer.type(), payer.type().label(), payer.adresCode(), ContactView.from(payer.contact()),
                    StatusView.from(payer.status()), payer.createdAt(), payer.updatedAt());
        }
    }

    record PayerSummaryView(UUID uuid, String socialReason, String nit, PayerType type, PayerStatus.Code status) {

        static PayerSummaryView from(Payer payer) {
            return new PayerSummaryView(payer.uuid(), payer.socialReason(), payer.nit().formatted(), payer.type(),
                    payer.status().code());
        }
    }

    record ContactView(String address, String phone, String billingEmail) {

        static ContactView from(com.ClinicaDeYmid.contracting_service.domain.ContactInfo contact) {
            return new ContactView(contact.address(), contact.phone(), contact.billingEmail());
        }
    }

    record StatusView(PayerStatus.Code code, String reason, Instant since, boolean contractable) {

        static StatusView from(PayerStatus status) {
            return switch (status) {
                case PayerStatus.Active active -> new StatusView(status.code(), null, null, true);
                case PayerStatus.Suspended suspended -> new StatusView(status.code(), suspended.reason(), suspended.since(), false);
                case PayerStatus.Deactivated deactivated -> new StatusView(status.code(), deactivated.reason(), deactivated.since(), false);
            };
        }
    }

    record RevisionView(long number, Instant revisedAt, String revisedBy, PayerHistory.ChangeType changeType, PayerView state) {

        static RevisionView from(PayerHistory.Revision revision) {
            return new RevisionView(revision.number(), revision.revisedAt(), revision.revisedBy(), revision.changeType(),
                    PayerView.from(revision.state()));
        }
    }
}
