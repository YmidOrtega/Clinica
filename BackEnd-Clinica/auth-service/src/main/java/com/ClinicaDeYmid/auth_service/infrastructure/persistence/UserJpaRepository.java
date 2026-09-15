package com.ClinicaDeYmid.auth_service.infrastructure.persistence;

import com.ClinicaDeYmid.auth_service.domain.user.Role;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface UserJpaRepository extends JpaRepository<User, Long> {

    Optional<User> findByUuid(UUID uuid);

    @Query("select u from User u where u.email.value = :email")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("select count(u) > 0 from User u where u.email.value = :email")
    boolean existsByEmail(@Param("email") String email);

    @Query("select count(u) > 0 from User u where u.role = :role and u.statusCode <> :status")
    boolean existsByRoleAndStatusNot(@Param("role") Role role, @Param("status") UserStatus.Code status);

    @Query("select count(u) from User u where u.role = :role and u.statusCode = :status")
    long countByRoleAndStatus(@Param("role") Role role, @Param("status") UserStatus.Code status);
}
