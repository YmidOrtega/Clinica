package com.ClinicaDeYmid.auth_service.infrastructure.persistence;

import com.ClinicaDeYmid.auth_service.domain.user.Role;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    @Query(value = "SELECT uuid FROM users WHERE role = :role AND status = :status ORDER BY id FOR UPDATE", nativeQuery = true)
    List<String> lockUuidsByRoleAndStatus(@Param("role") String role, @Param("status") String status);

    @Query("""
            select u from User u
            where (:prefix is null or u.email.value like :prefix or u.fullName.value like :prefix)
              and (:role is null or u.role = :role)
              and (:status is null or u.statusCode = :status)""")
    Page<User> search(@Param("prefix") String prefix, @Param("role") Role role, @Param("status") UserStatus.Code status, Pageable pageable);
}
