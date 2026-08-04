package com.ClinicaDeYmid.billing_service.module.sale.service;

import com.ClinicaDeYmid.billing_service.infra.exception.SaleOrderItemNotFoundException;
import com.ClinicaDeYmid.billing_service.module.config.service.TaxResolverService;
import com.ClinicaDeYmid.billing_service.module.pricing.service.PriceResolverService;
import com.ClinicaDeYmid.billing_service.module.sale.dto.SaleOrderItemRequestDto;
import com.ClinicaDeYmid.billing_service.module.sale.dto.SaleOrderItemResponseDto;
import com.ClinicaDeYmid.billing_service.module.sale.dto.SaleOrderResponseDto;
import com.ClinicaDeYmid.billing_service.module.sale.entity.SaleOrder;
import com.ClinicaDeYmid.billing_service.module.sale.entity.SaleOrderItem;
import com.ClinicaDeYmid.billing_service.module.sale.enums.SaleItemType;
import com.ClinicaDeYmid.billing_service.module.sale.mapper.SaleOrderItemMapper;
import com.ClinicaDeYmid.billing_service.module.sale.mapper.SaleOrderMapper;
import com.ClinicaDeYmid.billing_service.module.sale.repository.SaleOrderItemRepository;
import com.ClinicaDeYmid.billing_service.module.sale.repository.SaleOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaleOrderItemService {

    private final SaleOrderItemRepository itemRepository;
    private final SaleOrderRepository orderRepository;
    private final SaleOrderItemMapper itemMapper;
    private final SaleOrderMapper orderMapper;
    private final SaleOrderService saleOrderService;
    private final PriceResolverService priceResolver;
    private final TaxResolverService taxResolver;

    @Transactional(readOnly = true)
    public List<SaleOrderItemResponseDto> findByOrder(Long saleOrderId) {
        log.info("Consultando ítems de la orden ID: {}", saleOrderId);
        saleOrderService.getOrThrow(saleOrderId);
        return itemRepository.findBySaleOrderIdOrderByItemTypeAscDescriptionAsc(saleOrderId)
                .stream()
                .map(itemMapper::toResponseDto)
                .toList();
    }

    @Transactional
    public SaleOrderResponseDto addItem(Long saleOrderId, SaleOrderItemRequestDto dto) {
        log.info("Agregando ítem a la orden ID: {}", saleOrderId);
        SaleOrder order = saleOrderService.getWithItemsOrThrow(saleOrderId);
        saleOrderService.requireDraft(order);

        if (dto.portfolioId() != null
                && itemRepository.existsBySaleOrderIdAndPortfolioId(saleOrderId, dto.portfolioId())) {
            throw new IllegalStateException(
                    "El portafolio " + dto.portfolioId() + " ya está en la orden " + saleOrderId + ".");
        }

        try {
            SaleOrderItem item = itemMapper.toEntity(dto);
            item.setSaleOrder(order);
            applyResolvedPricing(order, item, dto);
            item.calculateSubtotal();
            order.getItems().add(item);
            order.recalculateTotals();

            SaleOrder saved = orderRepository.save(order);
            log.info("Ítem agregado a la orden ID: {}. Nuevo total: {}", saleOrderId, saved.getTotalAmount());
            return orderMapper.toResponseDto(saved);
        } catch (DataAccessException ex) {
            log.error("Error al agregar ítem a la orden ID: {}", saleOrderId, ex);
            throw ex;
        }
    }

    @Transactional
    public SaleOrderResponseDto updateItem(Long saleOrderId, Long itemId, SaleOrderItemRequestDto dto) {
        log.info("Actualizando ítem ID: {} de la orden ID: {}", itemId, saleOrderId);
        SaleOrder order = saleOrderService.getWithItemsOrThrow(saleOrderId);
        saleOrderService.requireDraft(order);
        SaleOrderItem item = requireItemOf(order, itemId);

        if (dto.portfolioId() != null
                && !dto.portfolioId().equals(item.getPortfolioId())
                && itemRepository.existsBySaleOrderIdAndPortfolioId(saleOrderId, dto.portfolioId())) {
            throw new IllegalStateException(
                    "El portafolio " + dto.portfolioId() + " ya está en la orden " + saleOrderId + ".");
        }

        try {
            itemMapper.updateEntity(dto, item);
            applyResolvedPricing(order, item, dto);
            item.calculateSubtotal();
            order.recalculateTotals();

            SaleOrder saved = orderRepository.save(order);
            log.info("Ítem ID: {} actualizado. Nuevo total de orden: {}", itemId, saved.getTotalAmount());
            return orderMapper.toResponseDto(saved);
        } catch (DataAccessException ex) {
            log.error("Error al actualizar ítem ID: {}", itemId, ex);
            throw ex;
        }
    }

    @Transactional
    public SaleOrderResponseDto removeItem(Long saleOrderId, Long itemId) {
        log.info("Eliminando ítem ID: {} de la orden ID: {}", itemId, saleOrderId);
        SaleOrder order = saleOrderService.getWithItemsOrThrow(saleOrderId);
        saleOrderService.requireDraft(order);
        SaleOrderItem item = requireItemOf(order, itemId);

        order.getItems().remove(item);
        order.recalculateTotals();

        SaleOrder saved = orderRepository.save(order);
        log.info("Ítem ID: {} eliminado. Nuevo total: {}", itemId, saved.getTotalAmount());
        return orderMapper.toResponseDto(saved);
    }

    @Transactional
    public SaleOrderItemResponseDto authorizeItem(Long saleOrderId, Long itemId, Long authorizationId) {
        log.info("Autorizando ítem ID: {} de la orden ID: {} con autorización ID: {}",
                itemId, saleOrderId, authorizationId);
        SaleOrderItem item = getOrThrow(itemId, saleOrderId);
        item.setAuthorized(true);
        item.setAuthorizationId(authorizationId);
        return itemMapper.toResponseDto(itemRepository.save(item));
    }

    /**
     * Fija el precio unitario y la tasa de impuesto del ítem desde el servidor.
     * <p>
     * Cuando el ítem identifica un servicio del catálogo (por {@code portfolioId} o
     * {@code codeCups}) el precio lo determina {@link PriceResolverService} siguiendo la
     * cadena override → manual → tarifa de contrato → precio base del portafolio, y el
     * {@code unitPrice} que venga en la petición se ignora: el cliente HTTP no fija lo que
     * se cobra.
     * <p>
     * Los ítems sin portafolio ni CUPS (conceptos manuales: {@code OTHER}, insumos no
     * catalogados) no tienen contra qué resolverse, así que ahí sí se exige un precio
     * explícito en la petición.
     */
    private void applyResolvedPricing(SaleOrder order, SaleOrderItem item, SaleOrderItemRequestDto dto) {
        boolean catalogued = item.getPortfolioId() != null || StringUtils.hasText(item.getCodeCups());

        if (catalogued) {
            PriceResolverService.PriceResolution resolution = priceResolver.resolve(
                    order.getContractId(), item.getPortfolioId(), item.getCodeCups());

            if (dto.unitPrice() != null && dto.unitPrice().compareTo(resolution.price()) != 0) {
                log.warn("Precio enviado ({}) ignorado para portafolio: {} cups: {}; se aplica {} resuelto por {}",
                        dto.unitPrice(), item.getPortfolioId(), item.getCodeCups(),
                        resolution.price(), resolution.source());
            }

            item.setUnitPrice(resolution.price());
        } else {
            if (dto.unitPrice() == null) {
                throw new IllegalStateException(
                        "El precio unitario es obligatorio para ítems sin portafolio ni código CUPS.");
            }
            log.info("Ítem manual sin catálogo; se usa el precio enviado: {}", dto.unitPrice());
            item.setUnitPrice(dto.unitPrice());
        }

        item.setTaxRate(resolveTaxRate(item.getItemType()));
    }

    private BigDecimal resolveTaxRate(SaleItemType itemType) {
        return itemType == SaleItemType.SUPPLY
                ? taxResolver.resolveRateForMedications()
                : taxResolver.resolveRateForServices();
    }

    /**
     * Obtiene el ítem de la colección ya cargada de la orden. Al buscarlo dentro de la
     * propia orden, la pertenencia queda garantizada por construcción y se evita una
     * consulta extra. Devuelve la instancia gestionada por el contexto de persistencia,
     * necesaria para que {@code orphanRemoval} actúe al eliminarla de la colección.
     */
    private SaleOrderItem requireItemOf(SaleOrder order, Long itemId) {
        return order.getItems().stream()
                .filter(i -> itemId.equals(i.getId()))
                .findFirst()
                .orElseThrow(() -> new SaleOrderItemNotFoundException(itemId, order.getId()));
    }

    /**
     * Variante para operaciones que no cargan la orden completa: exige en la propia
     * consulta que el ítem pertenezca a la orden indicada.
     */
    SaleOrderItem getOrThrow(Long itemId, Long saleOrderId) {
        return itemRepository.findByIdAndSaleOrderId(itemId, saleOrderId)
                .orElseThrow(() -> new SaleOrderItemNotFoundException(itemId, saleOrderId));
    }
}
