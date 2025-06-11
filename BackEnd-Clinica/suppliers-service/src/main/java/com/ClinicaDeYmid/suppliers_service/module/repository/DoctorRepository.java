package com.ClinicaDeYmid.suppliers_service.module.repository;

import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorRepository extends JpaRepository <Doctor, Long> {

}
