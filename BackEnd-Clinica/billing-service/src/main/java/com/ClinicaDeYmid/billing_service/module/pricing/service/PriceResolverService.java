package com.ClinicaDeYmid.billing_service.module.pricing.service;

import com.ClinicaDeYmid.billing_service.infra.exception.PriceNotResolvedException;
import com.ClinicaDeYmid.billing_service.infra.feignclient.ClientsContractClient;
import com.ClinicaDeYmid.billing_service.infra.feignclient.ClientsPortfolioClient;
import com.ClinicaDeYmid.billing_service.infra.feignclient.dto.ContractFeignDto;
import com.ClinicaDeYmid.billing_service.infra.feignclient.dto.PortfolioFeignDto;
import com.ClinicaDeYmid.billing_service.module.pricing.entity.ClientPriceOverride;
import com.ClinicaDeYmid.billing_service.module.pricing.entity.PriceManualItem;
import com.ClinicaDeYmid.billing_service.module.pricing.repository.ClientPriceOverrideRepository;
import com.ClinicaDeYmid.billing_service.module.pricing.repository.PriceManualItemRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceResolverService {

    private final ClientPriceOverrideRepository overrideRepository;
    private final PriceManualItemRepository manualItemRepository;
    private final ClientsContractClient contractClient;
    private final ClientsPortfolioClient portfolioClient;

    /**
     * Cadena de resolución de precio:
     *   1. Override activo y vigente (local)
     *   2. Ítem de manual activo por portfolioId o codeCups (local)
     *   3. Tarifa acordada del contrato (clients-service)
     *   4. Precio base del portafolio (clients-service)
     */
    @Transactional(readOnly = true)
    public PriceResolution resolve(Long contractId, Long portfolioId, String codeCups) {
        log.info("Resolviendo precio — contrato: {} portafolio: {} cups: {}",
                contractId, portfolioId, codeCups);

        Optional<ClientPriceOverride> override =
                overrideRepository.findValidOverride(contractId, portfolioId, LocalDate.now());
        if (override.isPresent()) {
            ClientPriceOverride o = override.get();
            BigDecimal effective = applyDiscount(o.getNegotiatedPrice(), o.getDiscountPercentage());
            log.info("Precio resuelto por override ID: {} → {}", o.getId(), effective);
            return new PriceResolution(effective, PriceSource.OVERRIDE, o.getId());
        }

        Optional<PriceManualItem> manualItem = (portfolioId != null || codeCups != null)
                ? manualItemRepository.findActiveItemsInManual(null, portfolioId, codeCups)
                        .stream().findFirst()
                : Optional.empty();

        if (manualItem.isPresent()) {
            PriceManualItem item = manualItem.get();
            log.info("Precio resuelto por manual ítem ID: {} → {}", item.getId(), item.getBasePrice());
            return new PriceResolution(item.getBasePrice(), PriceSource.PRICE_MANUAL, item.getId());
        }

        if (contractId != null) {
            try {
                ContractFeignDto contract = contractClient.getById(contractId);
                if (contract.agreedTariff() != null
                        && contract.agreedTariff().compareTo(BigDecimal.ZERO) > 0) {
                    log.info("Precio resuelto por tarifa del contrato ID: {} → {}",
                            contractId, contract.agreedTariff());
                    return new PriceResolution(contract.agreedTariff(),
                            PriceSource.CONTRACT_TARIFF, contractId);
                }
            } catch (FeignException e) {
                log.warn("No se pudo obtener tarifa del contrato ID: {} — {}", contractId, e.getMessage());
            }
        }

        if (portfolioId != null) {
            try {
                PortfolioFeignDto portfolio = portfolioClient.getById(portfolioId);
                if (portfolio.price() != null
                        && portfolio.price().compareTo(BigDecimal.ZERO) > 0) {
                    log.info("Precio resuelto por precio base del portafolio ID: {} → {}",
                            portfolioId, portfolio.price());
                    return new PriceResolution(portfolio.price(),
                            PriceSource.PORTFOLIO_BASE, portfolioId);
                }
            } catch (FeignException e) {
                log.warn("No se pudo obtener precio del portafolio ID: {} — {}", portfolioId, e.getMessage());
            }
        }

        log.warn("Precio no resuelto para contrato: {} portafolio: {}", contractId, portfolioId);
        throw new PriceNotResolvedException(contractId, portfolioId);
    }

    private BigDecimal applyDiscount(BigDecimal price, BigDecimal discountPct) {
        if (discountPct == null || discountPct.compareTo(BigDecimal.ZERO) == 0) return price;
        return price.multiply(BigDecimal.ONE
                .subtract(discountPct.divide(BigDecimal.valueOf(100))))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public record PriceResolution(
            BigDecimal price,
            PriceSource source,
            Long sourceId
    ) {}

    public enum PriceSource {
        OVERRIDE,
        PRICE_MANUAL,
        CONTRACT_TARIFF,
        PORTFOLIO_BASE
    }
}
