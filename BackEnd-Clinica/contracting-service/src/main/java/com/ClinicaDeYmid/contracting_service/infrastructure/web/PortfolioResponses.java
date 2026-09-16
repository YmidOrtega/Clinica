package com.ClinicaDeYmid.contracting_service.infrastructure.web;

import com.ClinicaDeYmid.contracting_service.application.PortfolioCommands;
import com.ClinicaDeYmid.contracting_service.domain.PortfolioItem;
import com.ClinicaDeYmid.contracting_service.domain.PortfolioItemStatus;
import com.ClinicaDeYmid.contracting_service.domain.ServiceCategory;

import java.time.Instant;
import java.util.UUID;

final class PortfolioResponses {

    private PortfolioResponses() {
    }

    record ItemView(UUID uuid, long version, String cupsCode, String clinicCode, String name, ServiceCategory category,
                    String categoryLabel, StatusView status, Instant createdAt, Instant updatedAt) {

        static ItemView from(PortfolioItem item) {
            return new ItemView(item.uuid(), item.version(), item.code().cups(), item.code().clinic(), item.name(),
                    item.category(), item.category().label(), StatusView.from(item.status()), item.createdAt(), item.updatedAt());
        }
    }

    record StatusView(PortfolioItemStatus.Code code, String reason, Instant since, boolean offered) {

        static StatusView from(PortfolioItemStatus status) {
            return switch (status) {
                case PortfolioItemStatus.Active active -> new StatusView(status.code(), null, null, true);
                case PortfolioItemStatus.Inactive inactive ->
                        new StatusView(status.code(), inactive.reason(), inactive.since(), false);
            };
        }
    }

    record ImportView(int created, int updated, int unchanged) {

        static ImportView from(PortfolioCommands.Import result) {
            return new ImportView(result.created(), result.updated(), result.unchanged());
        }
    }
}
