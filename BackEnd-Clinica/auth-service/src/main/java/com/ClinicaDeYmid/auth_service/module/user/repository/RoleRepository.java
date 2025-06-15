package com.ClinicaDeYmid.auth_service.module.user.repository;

import com.ClinicaDeYmid.auth_service.module.user.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
}
