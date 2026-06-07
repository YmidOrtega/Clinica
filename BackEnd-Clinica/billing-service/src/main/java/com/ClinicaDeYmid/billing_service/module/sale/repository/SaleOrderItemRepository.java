package com.ClinicaDeYmid.billing_service.module.sale.repository;

import com.ClinicaDeYmid.billing_service.module.sale.entity.SaleOrderItem;
import com.ClinicaDeYmid.billing_service.module.sale.enums.SaleItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SaleOrderItemRepository extends JpaRepository<SaleOrderItem, Long> {

    List<SaleOrderItem> findBySaleOrderIdOrderByItemTypeAscDescriptionAsc(Long saleOrderId);

    List<SaleOrderItem> findBySaleOrderIdAndItemType(Long saleOrderId, SaleItemType itemType);

    boolean existsBySaleOrderIdAndPortfolioId(Long saleOrderId, Long portfolioId);

    long countBySaleOrderId(Long saleOrderId);
}
