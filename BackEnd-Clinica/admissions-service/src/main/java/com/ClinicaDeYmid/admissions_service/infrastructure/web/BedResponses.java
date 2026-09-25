package com.ClinicaDeYmid.admissions_service.infrastructure.web;

import com.ClinicaDeYmid.admissions_service.domain.Bed;
import com.ClinicaDeYmid.admissions_service.domain.BedStatus;
import com.ClinicaDeYmid.admissions_service.domain.CatalogueStatus;
import com.ClinicaDeYmid.admissions_service.domain.Room;
import com.ClinicaDeYmid.admissions_service.domain.StayType;

import java.time.Instant;
import java.util.UUID;

final class BedResponses {

    record BedStatusView(BedStatus.Code code, UUID occupant, String reason, Instant since) {

        static BedStatusView from(BedStatus status) {
            return switch (status) {
                case BedStatus.Available ignored -> new BedStatusView(BedStatus.Code.AVAILABLE, null, null, null);
                case BedStatus.Occupied occupied ->
                        new BedStatusView(BedStatus.Code.OCCUPIED, occupied.occupant(), null, occupied.since());
                case BedStatus.Cleaning cleaning ->
                        new BedStatusView(BedStatus.Code.CLEANING, null, null, cleaning.since());
                case BedStatus.Maintenance maintenance ->
                        new BedStatusView(BedStatus.Code.MAINTENANCE, null, maintenance.reason(), maintenance.since());
                case BedStatus.Blocked blocked ->
                        new BedStatusView(BedStatus.Code.BLOCKED, null, blocked.reason(), blocked.since());
            };
        }
    }

    record RoomView(UUID uuid, String name, UUID locationUuid, String locationName, StayType stayType,
                    CatalogueStatus.Code status, String statusReason) {

        static RoomView from(Room room) {
            CatalogueStatus status = room.status();
            String reason = status instanceof CatalogueStatus.Retired retired ? retired.reason() : null;
            return new RoomView(room.uuid(), room.name(), room.location().uuid(), room.location().name(),
                    room.stayType(), status.code(), reason);
        }
    }

    record BedView(UUID uuid, String label, UUID roomUuid, String roomName, StayType stayType,
                   UUID locationUuid, String locationName, BedStatusView status) {

        static BedView from(Bed bed) {
            Room room = bed.room();
            return new BedView(bed.uuid(), bed.label(), room.uuid(), room.name(), room.stayType(),
                    room.location().uuid(), room.location().name(), BedStatusView.from(bed.status()));
        }
    }

    private BedResponses() {
    }
}
