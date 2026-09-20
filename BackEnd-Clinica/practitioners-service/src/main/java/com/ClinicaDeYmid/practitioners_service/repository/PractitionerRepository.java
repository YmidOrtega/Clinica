package com.ClinicaDeYmid.practitioners_service.repository;

import com.ClinicaDeYmid.practitioners_service.shared.DocumentType;
import com.ClinicaDeYmid.practitioners_service.repository.entity.Practitioner;
import com.ClinicaDeYmid.practitioners_service.shared.PractitionerStatusCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PractitionerRepository extends JpaRepository<Practitioner, Long> {

    @Query("""
            select p from Practitioner p
            left join fetch p.specialties assignment
            left join fetch assignment.specialty
            left join fetch assignment.subSpecialty
            where p.uuid = :uuid
            """)
    Optional<Practitioner> findByUuid(@Param("uuid") UUID uuid);

    @Query("""
            select p from Practitioner p
            where p.document.type = :type and p.document.number = :number
            """)
    Optional<Practitioner> findByDocument(@Param("type") DocumentType type, @Param("number") String number);

    @Query("select p from Practitioner p where p.registration.number = :number")
    Optional<Practitioner> findByRegistrationNumber(@Param("number") String number);

    @Query("select p from Practitioner p where p.contact.email = :email")
    Optional<Practitioner> findByEmail(@Param("email") String email);

    @Query("""
            select distinct p from Practitioner p
            left join fetch p.specialties assignment
            left join fetch assignment.specialty specialty
            left join fetch assignment.subSpecialty
            where (:status is null or p.statusCode = :status)
              and (:lastNames is null or lower(p.lastNames) like lower(concat(:lastNames, '%')))
              and (:specialtyCode is null or specialty.code = :specialtyCode)
            order by p.lastNames, p.firstNames
            """)
    List<Practitioner> search(@Param("status") PractitionerStatusCode status,
                              @Param("lastNames") String lastNames,
                              @Param("specialtyCode") String specialtyCode);
}
