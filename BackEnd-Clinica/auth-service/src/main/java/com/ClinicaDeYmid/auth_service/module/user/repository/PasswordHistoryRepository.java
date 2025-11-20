package com.ClinicaDeYmid.auth_service.module.user.repository;

import com.ClinicaDeYmid.auth_service.module.user.entity.PasswordHistory;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    /**
     * Obtiene las últimas N contraseñas de un usuario
     */
    @Query("""
        SELECT ph FROM PasswordHistory ph
        WHERE ph.user = :user
        ORDER BY ph.createdAt DESC
        LIMIT :limit
        """)
    List<PasswordHistory> findRecentPasswordsByUser(
            @Param("user") User user,
            @Param("limit") int limit
    );

    /**
     * Cuenta cuántas contraseñas ha usado un usuario
     */
    long countByUser(User user);

    /**
     * Elimina contraseñas antiguas excepto las últimas N
     */
    @Modifying
    @Query("""
        DELETE FROM PasswordHistory ph
        WHERE ph.user = :user
        AND ph.id NOT IN (
            SELECT ph2.id FROM PasswordHistory ph2
            WHERE ph2.user = :user
            ORDER BY ph2.createdAt DESC
            LIMIT :keepCount
        )
        """)
    void deleteOldPasswordsExceptRecent(
            @Param("user") User user,
            @Param("keepCount") int keepCount
    );

    /**
     * Busca todas las contraseñas de un usuario ordenadas
     */
    List<PasswordHistory> findByUserOrderByCreatedAtDesc(User user);

    /**
     * Elimina contraseñas más antiguas que una fecha
     */
    void deleteByCreatedAtBefore(LocalDateTime before);
}