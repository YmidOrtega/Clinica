package com.ClinicaDeYmid.auth_service.module.auth.repository;

import com.ClinicaDeYmid.auth_service.module.auth.entity.LoginAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    /**
     * Cuenta intentos fallidos recientes por email
     */
    @Query("""
        SELECT COUNT(la) FROM LoginAttempt la
        WHERE la.email = :email
        AND la.successful = false
        AND la.attemptedAt > :since
        """)
    long countFailedAttemptsSince(
            @Param("email") String email,
            @Param("since") LocalDateTime since
    );

    /**
     * Cuenta intentos fallidos recientes por IP
     */
    @Query("""
        SELECT COUNT(la) FROM LoginAttempt la
        WHERE la.ipAddress = :ipAddress
        AND la.successful = false
        AND la.attemptedAt > :since
        """)
    long countFailedAttemptsByIpSince(
            @Param("ipAddress") String ipAddress,
            @Param("since") LocalDateTime since
    );

    /**
     * Busca intentos por email (paginado)
     */
    Page<LoginAttempt> findByEmailOrderByAttemptedAtDesc(
            String email,
            Pageable pageable
    );

    /**
     * Busca intentos por IP (paginado)
     */
    Page<LoginAttempt> findByIpAddressOrderByAttemptedAtDesc(
            String ipAddress,
            Pageable pageable
    );

    /**
     * Busca todos los intentos fallidos recientes
     */
    @Query("""
        SELECT la FROM LoginAttempt la
        WHERE la.successful = false
        AND la.attemptedAt > :since
        ORDER BY la.attemptedAt DESC
        """)
    List<LoginAttempt> findRecentFailedAttempts(
            @Param("since") LocalDateTime since
    );

    /**
     * Elimina intentos antiguos (limpieza)
     */
    void deleteByAttemptedAtBefore(LocalDateTime before);

    /**
     * Obtiene el último intento exitoso de un email
     */
    @Query("""
        SELECT la FROM LoginAttempt la
        WHERE la.email = :email
        AND la.successful = true
        ORDER BY la.attemptedAt DESC
        LIMIT 1
        """)
    Optional<LoginAttempt> findLastSuccessfulAttempt(@Param("email") String email);
}