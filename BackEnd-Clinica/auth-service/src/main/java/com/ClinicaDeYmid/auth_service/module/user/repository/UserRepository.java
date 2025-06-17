package com.ClinicaDeYmid.auth_service.module.user.repository;

import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.ClinicaDeYmid.auth_service.module.user.enums.StatusUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Page<User> findByActiveTrue(Pageable pageable);

    Optional<User> findByUuid(String uuid);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmailAndIdNot(String email, Long id);
    boolean existsByUsernameAndIdNot(String username, Long id);

    Page<User> findByStatus(StatusUser status, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.role.id = :roleId")
    Page<User> findByRoleId(@Param("roleId") Long roleId, Pageable pageable);


    Page<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String username, String email, Pageable pageable);


    List<User> findByActiveTrueOrderByUsernameAsc();


    long countByActiveTrue();
    long countByStatus(StatusUser status);

    @Query("SELECT u FROM User u WHERE u.active = true AND u.status = :status")
    Page<User> findByActiveTrueAndStatus(@Param("status") StatusUser status, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.role.id = :roleId AND u.active = true")
    Page<User> findByRoleIdAndActiveTrue(@Param("roleId") Long roleId, Pageable pageable);


    @Query("SELECT u FROM User u WHERE " +
            "(:username IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%'))) AND " +
            "(:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%'))) AND " +
            "(:status IS NULL OR u.status = :status) AND " +
            "(:roleId IS NULL OR u.role.id = :roleId) AND " +
            "(:active IS NULL OR u.active = :active)")
    Page<User> findUsersWithFilters(@Param("username") String username,
                                    @Param("email") String email,
                                    @Param("status") StatusUser status,
                                    @Param("roleId") Long roleId,
                                    @Param("active") Boolean active,
                                    Pageable pageable);
}