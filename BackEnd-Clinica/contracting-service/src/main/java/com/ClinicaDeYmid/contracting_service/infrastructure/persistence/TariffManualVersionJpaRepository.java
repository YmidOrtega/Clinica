package com.ClinicaDeYmid.contracting_service.infrastructure.persistence;

import com.ClinicaDeYmid.contracting_service.domain.TariffManualVersion;
import com.ClinicaDeYmid.contracting_service.domain.TariffVersionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TariffManualVersionJpaRepository extends JpaRepository<TariffManualVersion, Long> {

    @Query("SELECT v FROM TariffManualVersion v JOIN FETCH v.manual WHERE v.uuid = :uuid")
    Optional<TariffManualVersion> findByUuid(@Param("uuid") UUID uuid);

    @Query("SELECT v FROM TariffManualVersion v JOIN FETCH v.manual m WHERE m.uuid = :manual "
            + "ORDER BY v.validFrom DESC, v.id DESC")
    List<TariffManualVersion> findByManual(@Param("manual") UUID manual);

    @Query("SELECT v FROM TariffManualVersion v JOIN FETCH v.manual m WHERE m.uuid = :manual AND v.statusCode = :status")
    Optional<TariffManualVersion> findByManualAndStatus(@Param("manual") UUID manual,
                                                        @Param("status") TariffVersionStatus.Code status);

    @Query("SELECT v FROM TariffManualVersion v JOIN FETCH v.manual m WHERE m.uuid = :manual AND v.validFrom <= :date "
            + "AND v.statusCode <> :draft ORDER BY v.validFrom DESC, v.id DESC")
    List<TariffManualVersion> findInForceOn(@Param("manual") UUID manual, @Param("date") LocalDate date,
                                            @Param("draft") TariffVersionStatus.Code draft, Pageable pageable);
}
