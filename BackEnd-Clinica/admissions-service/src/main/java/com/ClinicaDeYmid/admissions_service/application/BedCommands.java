package com.ClinicaDeYmid.admissions_service.application;

import com.ClinicaDeYmid.admissions_service.domain.AdmissionsException;
import com.ClinicaDeYmid.admissions_service.domain.Bed;
import com.ClinicaDeYmid.admissions_service.domain.BedStay;
import com.ClinicaDeYmid.admissions_service.domain.BedStays;
import com.ClinicaDeYmid.admissions_service.domain.Beds;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.Locations;
import com.ClinicaDeYmid.admissions_service.domain.Room;
import com.ClinicaDeYmid.admissions_service.domain.Rooms;
import com.ClinicaDeYmid.admissions_service.domain.StayType;
import com.ClinicaDeYmid.commons.web.EntityTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class BedCommands {

    private static final Logger log = LoggerFactory.getLogger(BedCommands.class);

    private final Rooms rooms;
    private final Beds beds;
    private final BedStays stays;
    private final Locations locations;
    private final TransactionOperations transactions;
    private final Clock clock;

    public BedCommands(Rooms rooms, Beds beds, BedStays stays, Locations locations,
                       TransactionOperations transactions, Clock clock) {
        this.rooms = rooms;
        this.beds = beds;
        this.stays = stays;
        this.locations = locations;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Room openRoom(String name, UUID locationUuid, StayType stayType) {
        return transactions.execute(status -> {
            Location location = locations.findByUuid(locationUuid)
                    .orElseThrow(AdmissionsException.LocationNotFound::new);
            rooms.findByNameAndLocation(name, locationUuid).ifPresent(other -> {
                throw new AdmissionsException.NameAlreadyUsed("las habitaciones de esa ubicación");
            });
            Room opened = rooms.save(Room.open(name, location, stayType));
            log.info("Room opened: uuid={} location={}", opened.uuid(), locationUuid);
            return opened;
        });
    }

    public Room retireRoom(UUID uuid, long expectedVersion, String reason) {
        return modifyRoom(uuid, expectedVersion, room -> room.retire(reason, clock));
    }

    public Room changeStayType(UUID uuid, long expectedVersion, StayType stayType) {
        return modifyRoom(uuid, expectedVersion, room -> room.changeStayType(stayType));
    }

    public Room restoreRoom(UUID uuid, long expectedVersion) {
        return modifyRoom(uuid, expectedVersion, Room::restore);
    }

    public Bed installBed(String label, UUID roomUuid) {
        return transactions.execute(status -> {
            Room room = rooms.findByUuid(roomUuid).orElseThrow(AdmissionsException.RoomNotFound::new);
            beds.findByLabelAndRoom(label, roomUuid).ifPresent(other -> {
                throw new AdmissionsException.NameAlreadyUsed("las camas de esa habitación");
            });
            Bed installed = beds.save(Bed.install(label, room));
            log.info("Bed installed: uuid={} room={}", installed.uuid(), roomUuid);
            return installed;
        });
    }

    public Bed occupy(UUID bedUuid, UUID occupant) {
        return transactions.execute(status -> {
            Bed bed = beds.lockByUuid(bedUuid).orElseThrow(AdmissionsException.BedNotFound::new);
            Instant now = Instant.now(clock);
            bed.occupy(occupant, clock);
            try {
                stays.save(BedStay.begin(bed, occupant, now));
            } catch (DataIntegrityViolationException overlapping) {
                log.warn("Bed stay refused by the database: bed={} occupant={}", bedUuid, occupant);
                throw new AdmissionsException.BedAlreadyTaken();
            }
            beds.save(bed);
            log.info("Bed occupied: uuid={} occupant={}", bedUuid, occupant);
            return fetched(bedUuid);
        });
    }

    public Bed release(UUID bedUuid) {
        return transactions.execute(status -> {
            Bed bed = beds.lockByUuid(bedUuid).orElseThrow(AdmissionsException.BedNotFound::new);
            BedStay stay = stays.findOpenByBed(bedUuid).orElseThrow(AdmissionsException.BedNotOccupied::new);
            bed.release(clock);
            stay.end(clock);
            stays.save(stay);
            beds.save(bed);
            log.info("Bed released: uuid={}", bedUuid);
            return fetched(bedUuid);
        });
    }

    public Bed finishCleaning(UUID bedUuid, long expectedVersion) {
        return modifyBed(bedUuid, expectedVersion, Bed::finishCleaning);
    }

    public Bed sendToMaintenance(UUID bedUuid, long expectedVersion, String reason) {
        return modifyBed(bedUuid, expectedVersion, bed -> bed.sendToMaintenance(reason, clock));
    }

    public Bed block(UUID bedUuid, long expectedVersion, String reason) {
        return modifyBed(bedUuid, expectedVersion, bed -> bed.block(reason, clock));
    }

    public Bed returnToService(UUID bedUuid, long expectedVersion) {
        return modifyBed(bedUuid, expectedVersion, Bed::returnToService);
    }

    private Bed fetched(UUID bedUuid) {
        return beds.findByUuid(bedUuid).orElseThrow(AdmissionsException.BedNotFound::new);
    }

    private Room modifyRoom(UUID uuid, long expectedVersion, Consumer<Room> change) {
        return transactions.execute(status -> {
            Room room = rooms.findByUuid(uuid).orElseThrow(AdmissionsException.RoomNotFound::new);
            requireVersion(room.version(), expectedVersion);
            change.accept(room);
            return rooms.save(room);
        });
    }

    private Bed modifyBed(UUID uuid, long expectedVersion, Consumer<Bed> change) {
        return transactions.execute(status -> {
            Bed bed = beds.findByUuid(uuid).orElseThrow(AdmissionsException.BedNotFound::new);
            requireVersion(bed.version(), expectedVersion);
            change.accept(bed);
            return beds.save(bed);
        });
    }

    private static void requireVersion(long actual, long expected) {
        if (actual != expected) {
            throw new EntityTags.StaleVersion();
        }
    }
}
