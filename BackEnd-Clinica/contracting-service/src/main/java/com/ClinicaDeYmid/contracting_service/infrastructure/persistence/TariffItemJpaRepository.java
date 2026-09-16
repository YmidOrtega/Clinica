package com.ClinicaDeYmid.contracting_service.infrastructure.persistence;

import com.ClinicaDeYmid.contracting_service.domain.TariffItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TariffItemJpaRepository extends JpaRepository<TariffItem, Long> {

    @Query("SELECT i FROM TariffItem i JOIN FETCH i.manualVersion v JOIN FETCH v.manual "
            + "WHERE v.uuid = :version AND i.cupsCode = :code")
    Optional<TariffItem> findByVersionAndCode(@Param("version") UUID version, @Param("code") String code);

    @Query("SELECT i FROM TariffItem i JOIN FETCH i.manualVersion v JOIN FETCH v.manual "
            + "WHERE v.uuid = :version ORDER BY i.cupsCode")
    List<TariffItem> findByVersion(@Param("version") UUID version, Pageable pageable);
}
