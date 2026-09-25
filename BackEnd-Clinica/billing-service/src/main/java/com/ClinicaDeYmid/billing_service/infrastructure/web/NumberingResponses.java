package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.application.NumberingQueries.ResolutionState;
import com.ClinicaDeYmid.billing_service.domain.DianEnvironment;
import com.ClinicaDeYmid.billing_service.domain.NumberingResolution;
import com.ClinicaDeYmid.billing_service.domain.ResolutionAlert;
import com.ClinicaDeYmid.billing_service.domain.ResolutionStatus;
import com.ClinicaDeYmid.billing_service.domain.ResolutionTerms;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

final class NumberingResponses {

    record StatusView(ResolutionStatus.Code code, String reason, Instant since) {

        static StatusView from(ResolutionStatus status) {
            return switch (status) {
                case ResolutionStatus.Pending ignored -> new StatusView(ResolutionStatus.Code.PENDING, null, null);
                case ResolutionStatus.Active active -> new StatusView(ResolutionStatus.Code.ACTIVE, null, active.since());
                case ResolutionStatus.Exhausted exhausted ->
                        new StatusView(ResolutionStatus.Code.EXHAUSTED, null, exhausted.since());
                case ResolutionStatus.Retired retired ->
                        new StatusView(ResolutionStatus.Code.RETIRED, retired.reason(), retired.since());
            };
        }
    }

    record ResolutionView(UUID uuid, String resolutionNumber, LocalDate issuedOn, String prefix, long rangeFrom,
                          long rangeTo, LocalDate validFrom, LocalDate validUntil, String technicalKeyEnding,
                          DianEnvironment environment, StatusView status, long nextNumber, long remaining,
                          List<ResolutionAlert> alerts) {

        static ResolutionView from(ResolutionState state) {
            NumberingResolution resolution = state.resolution();
            ResolutionTerms terms = resolution.terms();
            List<ResolutionAlert> alerts = state.alerts().isEmpty() ? List.of()
                    : List.copyOf(EnumSet.copyOf(state.alerts()));
            return new ResolutionView(resolution.uuid(), terms.resolutionNumber(), terms.issuedOn(), terms.prefix(),
                    terms.rangeFrom(), terms.rangeTo(), terms.validFrom(), terms.validUntil(),
                    terms.technicalKey().substring(terms.technicalKey().length() - 4), resolution.environment(),
                    StatusView.from(resolution.status()), state.nextNumber(), state.remaining(), alerts);
        }
    }

    private NumberingResponses() {
    }
}
