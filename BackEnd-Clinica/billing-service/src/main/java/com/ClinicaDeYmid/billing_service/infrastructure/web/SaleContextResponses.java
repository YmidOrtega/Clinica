package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.application.context.EpisodeDetails;
import com.ClinicaDeYmid.billing_service.application.context.PatientDetails;
import com.ClinicaDeYmid.billing_service.application.context.PayerDetails;
import com.ClinicaDeYmid.billing_service.application.context.SaleContext;
import com.ClinicaDeYmid.billing_service.infrastructure.web.AccountResponses.AccountView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class SaleContextResponses {

    record PatientView(UUID uuid, boolean identified, String documentType, String documentNumber, String fullName,
                       LocalDate birthDate, String sex, String healthRegime, String code, Integer estimatedBirthYear) {

        static PatientView from(PatientDetails patient) {
            return switch (patient) {
                case null -> null;
                case PatientDetails.Registered registered -> new PatientView(registered.uuid(), true,
                        registered.documentType(), registered.documentNumber(),
                        registered.firstNames() + " " + registered.lastNames(), registered.birthDate(),
                        registered.sex(), registered.healthRegime(), null, null);
                case PatientDetails.Unidentified unidentified -> new PatientView(unidentified.uuid(), false, null,
                        null, null, null, unidentified.sex(), null, unidentified.code(),
                        unidentified.estimatedBirthYear());
            };
        }
    }

    record PayerView(UUID uuid, String name, String nit, String type, UUID contractUuid, String contractNumber,
                     String coverage, String coverageDetail) {

        static PayerView from(EpisodeDetails.Coverage coverage, PayerDetails payer) {
            if (coverage == null) {
                return null;
            }
            return new PayerView(coverage.payerUuid(), payer == null ? null : payer.name(),
                    payer == null ? null : payer.nit(), payer == null ? null : payer.type(), coverage.contractUuid(),
                    coverage.contractNumber(), coverage.status(), coverage.detail());
        }
    }

    record AuthorizationView(UUID uuid, String number, String type, String authorizedBy, BigDecimal copayment,
                             LocalDate validFrom, LocalDate validTo, Set<UUID> authorizedItems,
                             boolean coversEverything) {

        static AuthorizationView from(EpisodeDetails.Authorization authorization) {
            return new AuthorizationView(authorization.uuid(), authorization.number(), authorization.type(),
                    authorization.authorizedBy(), authorization.copayment(), authorization.validFrom(),
                    authorization.validTo(), authorization.authorizedItems(), authorization.coversEverything());
        }
    }

    record EpisodeView(String cause, String kind, String status, EpisodeDetails.Phase currentPhase,
                       EpisodeDetails.Attending attending) {
    }

    record SaleContextView(AccountView account, EpisodeView episode, PatientView patient, PayerView payer,
                           List<AuthorizationView> authorizations, boolean complete, List<String> warnings) {

        static SaleContextView from(SaleContext context) {
            EpisodeDetails episode = context.episode();
            return new SaleContextView(AccountView.from(context.account()),
                    new EpisodeView(episode.cause(), episode.kind(), episode.status(), episode.currentPhase(),
                            episode.attending()),
                    PatientView.from(context.patient()), PayerView.from(episode.coverage(), context.payer()),
                    episode.authorizations().stream().map(AuthorizationView::from).toList(), context.complete(),
                    context.warnings());
        }
    }

    private SaleContextResponses() {
    }
}
