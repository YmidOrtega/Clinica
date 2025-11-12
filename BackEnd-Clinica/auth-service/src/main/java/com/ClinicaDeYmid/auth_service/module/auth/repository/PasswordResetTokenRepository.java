package com.ClinicaDeYmid.auth_service.module.auth.repository;

import com.ClinicaDeYmid.auth_service.module.auth.entity.PasswordResetToken;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Busca un token válido por su valor
     */
    @Query("""
        SELECT prt FROM PasswordResetToken prt
        WHERE prt.token = :token
        AND prt.used = false
        AND prt.expiresAt > :now
        """)
    Optional<PasswordResetToken> findValidToken(
            @Param("token") String token,
            @Param("now") LocalDateTime now
    );

    /**
     * Busca token por su valor (sin validar expiración)
     */
    Optional<PasswordResetToken> findByToken(String token);

    /**
     * Busca tokens de un usuario
     */
    @Query("""
        SELECT prt FROM PasswordResetToken prt
        WHERE prt.user = :user
        ORDER BY prt.createdAt DESC
        """)
    Optional<PasswordResetToken> findLatestByUser(@Param("user") User user);

    /**
     * Invalida todos los tokens de un usuario
     */
    @Modifying
    @Query("""
        UPDATE PasswordResetToken prt
        SET prt.used = true, prt.usedAt = :now
        WHERE prt.user = :user
        AND prt.used = false
        """)
    int invalidateAllUserTokens(
            @Param("user") User user,
            @Param("now") LocalDateTime now
    );

    /**
     * Elimina tokens expirados (limpieza)
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken prt WHERE prt.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);

    /**
     * Verifica si existe un token válido para un usuario
     */
    @Query("""
        SELECT CASE WHEN COUNT(prt) > 0 THEN true ELSE false END
        FROM PasswordResetToken prt
        WHERE prt.user = :user
        AND prt.used = false
        AND prt.expiresAt > :now
        """)
    boolean hasValidToken(
            @Param("user") User user,
            @Param("now") LocalDateTime now
    );
}