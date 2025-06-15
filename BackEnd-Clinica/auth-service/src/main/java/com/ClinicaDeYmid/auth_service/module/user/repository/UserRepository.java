package com.ClinicaDeYmid.auth_service.module.user.repository;

import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

public interface UserRepository extends JpaRepository <User, Long> {

    boolean existsByUsername(@NotBlank String username);

    Page<User> findByActiveTrue(Pageable pagination);

    UserDetails findByEmail(String email);

    Optional<User> findByUuid(String uuid);

}
