package com.ClinicaDeYmid.billing_service.module.pricing.repository;

import com.ClinicaDeYmid.billing_service.module.pricing.entity.PriceManualItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PriceManualItemRepository extends JpaRepository<PriceManualItem, Long> {

    List<PriceManualItem> findByPriceManualIdAndActiveTrueOrderByDescriptionAsc(Long priceManualId);

    Optional<PriceManualItem> findByPriceManualIdAndPortfolioId(Long priceManualId, Long portfolioId);

    boolean existsByPriceManualIdAndPortfolioId(Long priceManualId, Long portfolioId);

    List<PriceManualItem> findByPortfolioIdAndActiveTrue(Long portfolioId);

    List<PriceManualItem> findByCodeCupsAndActiveTrue(String codeCups);

    /**
     * Ítems activos de manuales activos que coincidan por portafolio o por código CUPS.
     * <p>
     * {@code manualId} es opcional: cuando es null se busca en todos los manuales activos.
     * Devuelve lista (no Optional) porque el mismo portafolio puede estar en varios manuales;
     * el orden por {@code basePrice} garantiza que el primero sea el más económico.
     */
    @Query("""
            SELECT i FROM PriceManualItem i
            WHERE i.priceManual.active = true
            AND i.active = true
            AND (:manualId IS NULL OR i.priceManual.id = :manualId)
            AND (i.portfolioId = :portfolioId OR i.codeCups = :codeCups)
            ORDER BY i.basePrice ASC
            """)
    List<PriceManualItem> findActiveItemsInManual(
            @Param("manualId") Long manualId,
            @Param("portfolioId") Long portfolioId,
            @Param("codeCups") String codeCups);
}
