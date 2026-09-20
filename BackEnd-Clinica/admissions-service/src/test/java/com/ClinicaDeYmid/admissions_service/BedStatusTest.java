package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.domain.AdmissionsException;
import com.ClinicaDeYmid.admissions_service.domain.Bed;
import com.ClinicaDeYmid.admissions_service.domain.BedStatus;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.Room;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BedStatusTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-20T10:15:30Z"), ZoneOffset.UTC);
    private final UUID occupant = UUID.randomUUID();

    @Test
    void aNewBedIsAvailable() {
        assertThat(bed().status()).isInstanceOf(BedStatus.Available.class);
        assertThat(bed().status().free()).isTrue();
    }

    @Test
    void occupyingRecordsWhoAndWhen() {
        Bed bed = bed();

        bed.occupy(occupant, clock);

        assertThat(bed.status()).isEqualTo(new BedStatus.Occupied(occupant, Instant.parse("2026-09-20T10:15:30Z")));
        assertThat(bed.status().taken()).isTrue();
    }

    @Test
    void anOccupiedBedCannotBeTakenAgain() {
        Bed bed = bed();
        bed.occupy(occupant, clock);

        assertThatThrownBy(() -> bed.occupy(UUID.randomUUID(), clock))
                .isInstanceOf(AdmissionsException.BedNotAvailable.class);
    }

    @Test
    void releasingSendsTheBedToCleaningNotStraightToAvailable() {
        Bed bed = bed();
        bed.occupy(occupant, clock);

        bed.release(clock);

        assertThat(bed.status()).isInstanceOf(BedStatus.Cleaning.class);
        assertThat(bed.status().free()).isFalse();

        bed.finishCleaning();
        assertThat(bed.status()).isInstanceOf(BedStatus.Available.class);
    }

    @Test
    void aBedThatWasNeverOccupiedCannotBeReleased() {
        assertThatThrownBy(() -> bed().release(clock)).isInstanceOf(AdmissionsException.BedNotOccupied.class);
    }

    @Test
    void anOccupiedBedGoesNeitherToMaintenanceNorToBlocked() {
        Bed bed = bed();
        bed.occupy(occupant, clock);

        assertThatThrownBy(() -> bed.sendToMaintenance("Cambio de colchón", clock))
                .isInstanceOf(AdmissionsException.BedNotAvailable.class);
        assertThatThrownBy(() -> bed.block("Aislamiento", clock))
                .isInstanceOf(AdmissionsException.BedNotAvailable.class);
    }

    @Test
    void maintenanceAndBlockingKeepTheirReasonAndComeBackToService() {
        Bed maintained = bed();
        maintained.sendToMaintenance("Cambio de colchón", clock);
        assertThat(maintained.status())
                .isEqualTo(new BedStatus.Maintenance("Cambio de colchón", Instant.parse("2026-09-20T10:15:30Z")));
        maintained.returnToService();
        assertThat(maintained.status()).isInstanceOf(BedStatus.Available.class);

        Bed blocked = bed();
        blocked.block("Aislamiento por brote", clock);
        assertThat(blocked.status())
                .isEqualTo(new BedStatus.Blocked("Aislamiento por brote", Instant.parse("2026-09-20T10:15:30Z")));
        blocked.returnToService();
        assertThat(blocked.status()).isInstanceOf(BedStatus.Available.class);
    }

    @Test
    void maintenanceAndBlockingDemandAReason() {
        assertThatThrownBy(() -> bed().sendToMaintenance(" ", clock))
                .isInstanceOf(AdmissionsException.InvalidData.class);
        assertThatThrownBy(() -> bed().block(null, clock))
                .isInstanceOf(AdmissionsException.InvalidData.class);
    }

    @Test
    void anAvailableBedCannotBeReturnedToService() {
        assertThatThrownBy(() -> bed().returnToService()).isInstanceOf(AdmissionsException.BedNotAvailable.class);
    }

    @Test
    void aBedCannotBeInstalledInARetiredRoom() {
        Room room = Room.open("301", Location.define("Piso 3"));
        room.retire("Remodelación", clock);

        assertThatThrownBy(() -> Bed.install("301-A", room)).isInstanceOf(AdmissionsException.RetiredRoom.class);
    }

    @Test
    void aRoomCannotBeOpenedInARetiredLocation() {
        Location location = Location.define("Piso 3");
        location.retire("Remodelación", clock);

        assertThatThrownBy(() -> Room.open("301", location)).isInstanceOf(AdmissionsException.RetiredLocation.class);
    }

    private Bed bed() {
        return Bed.install("301-A", Room.open("301", Location.define("Piso 3")));
    }
}
