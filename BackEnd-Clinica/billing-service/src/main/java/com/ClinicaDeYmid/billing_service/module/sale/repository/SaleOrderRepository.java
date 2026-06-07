package com.ClinicaDeYmid.billing_service.module.sale.repository;

import com.ClinicaDeYmid.billing_service.module.sale.entity.SaleOrder;
import com.ClinicaDeYmid.billing_service.module.sale.enums.SaleOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SaleOrderRepository extends JpaRepository<SaleOrder, Long> {

    List<SaleOrder> findByAttentionIdOrderByCreatedAtDesc(Long attentionId);

    Optional<SaleOrder> findByAttentionIdAndStatus(Long attentionId, SaleOrderStatus status);

    boolean existsByAttentionIdAndStatus(Long attentionId, SaleOrderStatus status);

    Page<SaleOrder> findByPatientIdOrderByCreatedAtDesc(String patientId, Pageable pageable);

    Page<SaleOrder> findByStatusOrderByCreatedAtDesc(SaleOrderStatus status, Pageable pageable);

    Page<SaleOrder> findByHealthProviderNitAndStatusOrderByCreatedAtDesc(
            String healthProviderNit, SaleOrderStatus status, Pageable pageable);

    @Query("""
            SELECT s FROM SaleOrder s
            WHERE s.contractId = :contractId
            AND s.status = :status
            ORDER BY s.createdAt DESC
            """)
    Page<SaleOrder> findByContractIdAndStatus(
            @Param("contractId") Long contractId,
            @Param("status") SaleOrderStatus status,
            Pageable pageable);

    @Query("""
            SELECT s FROM SaleOrder s
            LEFT JOIN FETCH s.items
            WHERE s.id = :id
            """)
    Optional<SaleOrder> findByIdWithItems(@Param("id") Long id);
}
