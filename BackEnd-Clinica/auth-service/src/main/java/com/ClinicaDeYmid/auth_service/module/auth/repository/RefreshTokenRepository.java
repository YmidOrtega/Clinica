package com.ClinicaDeYmid.auth_service.module.auth.repository;

import com.ClinicaDeYmid.auth_service.module.auth.entity.RefreshToken;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Busca un refresh token por su valor
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Busca todos los refresh tokens válidos de un usuario
     */
    @Query("""
        SELECT rt FROM RefreshToken rt
        WHERE rt.user = :user
        AND rt.revoked = false
        AND rt.expiresAt > :now
        ORDER BY rt.createdAt DESC
        """)
    List<RefreshToken> findValidTokensByUser(
            @Param("user") User user,
            @Param("now") LocalDateTime now
    );

    /**
     * Busca todos los tokens de un usuario (válidos e inválidos)
     */
    List<RefreshToken> findByUserOrderByCreatedAtDesc(User user);

    /**
     * Revoca todos los tokens de un usuario
     */
    @Modifying
    @Query("""
        UPDATE RefreshToken rt
        SET rt.revoked = true, rt.revokedAt = :now
        WHERE rt.user = :user
        AND rt.revoked = false
        """)
    int revokeAllUserTokens(
            @Param("user") User user,
            @Param("now") LocalDateTime now
    );

    /**
     * Elimina tokens expirados (limpieza)
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);

    /**
     * Cuenta tokens válidos de un usuario
     */
    @Query("""
        SELECT COUNT(rt) FROM RefreshToken rt
        WHERE rt.user = :user
        AND rt.revoked = false
        AND rt.expiresAt > :now
        """)
    long countValidTokensByUser(
            @Param("user") User user,
            @Param("now") LocalDateTime now
    );

    /**
     * Verifica si existe un token válido
     */
    @Query("""
        SELECT CASE WHEN COUNT(rt) > 0 THEN true ELSE false END
        FROM RefreshToken rt
        WHERE rt.token = :token
        AND rt.revoked = false
        AND rt.expiresAt > :now
        """)
    boolean existsValidToken(
            @Param("token") String token,
            @Param("now") LocalDateTime now
    );
}