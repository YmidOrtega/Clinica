package com.ClinicaDeYmid.billing_service.module.sale.repository;

import com.ClinicaDeYmid.billing_service.module.sale.entity.SaleOrderItem;
import com.ClinicaDeYmid.billing_service.module.sale.enums.SaleItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SaleOrderItemRepository extends JpaRepository<SaleOrderItem, Long> {

    /**
     * Busca un ítem exigiendo que pertenezca a la orden indicada. Se usa en lugar de
     * {@code findById} para que el {@code saleOrderId} de la ruta no pueda desacoplarse
     * del ítem sobre el que se opera.
     */
    Optional<SaleOrderItem> findByIdAndSaleOrderId(Long id, Long saleOrderId);

    List<SaleOrderItem> findBySaleOrderIdOrderByItemTypeAscDescriptionAsc(Long saleOrderId);

    List<SaleOrderItem> findBySaleOrderIdAndItemType(Long saleOrderId, SaleItemType itemType);

    boolean existsBySaleOrderIdAndPortfolioId(Long saleOrderId, Long portfolioId);

    long countBySaleOrderId(Long saleOrderId);
}
