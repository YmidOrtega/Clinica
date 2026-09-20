package com.ClinicaDeYmid.admissions_service.application;

import com.ClinicaDeYmid.admissions_service.domain.AdmissionsException;
import com.ClinicaDeYmid.admissions_service.domain.Bed;
import com.ClinicaDeYmid.admissions_service.domain.Beds;
import com.ClinicaDeYmid.admissions_service.domain.Room;
import com.ClinicaDeYmid.admissions_service.domain.Rooms;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class BedQueries {

    private final Rooms rooms;
    private final Beds beds;

    public BedQueries(Rooms rooms, Beds beds) {
        this.rooms = rooms;
        this.beds = beds;
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
}
