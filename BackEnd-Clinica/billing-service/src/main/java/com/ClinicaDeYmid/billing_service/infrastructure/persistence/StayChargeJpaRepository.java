package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.StayCharge;
import com.ClinicaDeYmid.billing_service.domain.StayType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface StayChargeJpaRepository extends JpaRepository<StayCharge, Long> {

    @Query("select c from StayCharge c where c.stayType = :type")
    Optional<StayCharge> findByStayType(@Param("type") StayType type);

    @Query("select c from StayCharge c order by c.stayType")
    List<StayCharge> findAllOrdered();
}
