package com.ClinicaDeYmid.auth_service.module.user.repository;

import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.ClinicaDeYmid.auth_service.module.user.enums.StatusUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUuid(String uuid);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    Page<User> findByActiveTrue(Pageable pageable);

    Page<User> findByRoleId(Long roleId, Pageable pageable);

    Page<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(String username, String email, Pageable pageable);

    List<User> findByActiveTrueOrderByUsernameAsc();

    long countByActiveTrue();

    long countByStatus(StatusUser status);

    /**
     * Verifica si existe un email diferente al ID dado
     */
    boolean existsByEmailAndIdNot(String email, Long id);

    /**
     * Verifica si existe un username diferente al ID dado
     */
    boolean existsByUsernameAndIdNot(String username, Long id);

    /**
     * Busca usuarios por estado (paginado)
     */
    Page<User> findByStatus(StatusUser status, Pageable pageable);

    /**
     * Incrementa el contador de intentos fallidos
     */
    @Modifying
    @Query("""
        UPDATE User u
        SET u.failedLoginAttempts = u.failedLoginAttempts + 1
        WHERE u.id = :userId
        """)
    void incrementFailedLoginAttempts(@Param("userId") Long userId);

    /**
     * Resetea el contador de intentos fallidos
     */
    @Modifying
    @Query("""
        UPDATE User u
        SET u.failedLoginAttempts = 0, u.accountLockedUntil = null
        WHERE u.id = :userId
        """)
    void resetFailedLoginAttempts(@Param("userId") Long userId);

    /**
     * Bloquea una cuenta hasta una fecha
     */
    @Modifying
    @Query("""
        UPDATE User u
        SET u.accountLockedUntil = :lockUntil, u.status = :status
        WHERE u.id = :userId
        """)
    void lockAccountUntil(@Param("userId") Long userId, @Param("lockUntil") LocalDateTime lockUntil, @Param("status") StatusUser status);

    /**
     * Busca cuentas bloqueadas que ya pueden desbloquearse
     */
    @Query("""
        SELECT u FROM User u
        WHERE u.accountLockedUntil IS NOT NULL
        AND u.accountLockedUntil < :now
        AND u.status = :lockedStatus
        """)
    List<User> findAccountsToUnlock(@Param("now") LocalDateTime now, @Param("lockedStatus") StatusUser lockedStatus);

    /**
     * Actualiza la fecha del último cambio de contraseña
     */
    @Modifying
    @Query("""
        UPDATE User u 
        SET u.lastPasswordChange = :changeDate, 
            u.requirePasswordChange = false 
        WHERE u.id = :userId
        """)
    void updateLastPasswordChange(@Param("userId") Long userId, @Param("changeDate") LocalDateTime changeDate);

    /**
     * Busca usuarios que necesitan cambiar contraseña
     */
    @Query("""
        SELECT u FROM User u
        WHERE u.requirePasswordChange = true
        OR (u.passwordNeverExpires = false
            AND u.lastPasswordChange < :expirationDate)
        """)
    List<User> findUsersRequiringPasswordChange(@Param("expirationDate") LocalDateTime expirationDate);

    /**
     * Marca que un usuario necesita cambiar contraseña
     */
    @Modifying
    @Query("""
        UPDATE User u
        SET u.requirePasswordChange = true
        WHERE u.id = :userId
        """)
    void requirePasswordChange(@Param("userId") Long userId);
}