package com.ClinicaDeYmid.suppliers_service.module.repository;

import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// En tu DoctorRepository, agrega esta consulta
@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    @Query("""
        SELECT DISTINCT d FROM Doctor d 
        LEFT JOIN FETCH d.specialties s 
        LEFT JOIN FETCH s.subSpecialties ss
        LEFT JOIN FETCH d.subSpecialties ds
        WHERE d.id = :id
        """)
    Optional<Doctor> findByIdWithSpecialtiesAndSubSpecialties(@Param("id") Long id);

    @Query("""
        SELECT DISTINCT d FROM Doctor d 
        LEFT JOIN FETCH d.specialties s 
        LEFT JOIN FETCH s.subSpecialties ss
        LEFT JOIN FETCH d.subSpecialties ds
        WHERE d.active = true
        """)
    List<Doctor> findAllActiveWithSpecialtiesAndSubSpecialties();
}
