package com.ClinicaDeYmid.admissions_service.application;

import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.Admissions;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionsException;
import com.ClinicaDeYmid.admissions_service.domain.Bed;
import com.ClinicaDeYmid.admissions_service.domain.Beds;
import com.ClinicaDeYmid.admissions_service.domain.Room;
import com.ClinicaDeYmid.admissions_service.domain.Rooms;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class BedQueries {

    private final Rooms rooms;
    private final Beds beds;
    private final Admissions admissions;

    public BedQueries(Rooms rooms, Beds beds, Admissions admissions) {
        this.rooms = rooms;
        this.beds = beds;
        this.admissions = admissions;
    }

    public Room room(UUID uuid) {
        return rooms.findByUuid(uuid).orElseThrow(AdmissionsException.RoomNotFound::new);
    }

    public List<Room> roomsOf(UUID locationUuid) {
        return rooms.findByLocation(locationUuid);
    }

    public Bed bed(UUID uuid) {
        return beds.findByUuid(uuid).orElseThrow(AdmissionsException.BedNotFound::new);
    }

    public List<Bed> bedsOf(UUID roomUuid) {
        return beds.findByRoom(roomUuid);
    }

    public List<Bed> availableIn(UUID locationUuid) {
        return beds.findAvailableInLocation(locationUuid);
    }

    public List<CensusEntry> censusOf(UUID locationUuid) {
        List<Bed> installed = beds.findByLocation(locationUuid);
        Map<UUID, Admission> occupants = admissions.findByBeds(installed.stream()
                        .filter(bed -> bed.status().taken())
                        .map(Bed::uuid)
                        .toList()).stream()
                .filter(Admission::occupiesABed)
                .collect(Collectors.toMap(Admission::bedUuid, admission -> admission));
        return installed.stream().map(bed -> new CensusEntry(bed, occupants.get(bed.uuid()))).toList();
    }
}
