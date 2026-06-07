package com.ClinicaDeYmid.billing_service.module.sale.service;

import com.ClinicaDeYmid.billing_service.infra.exception.SaleOrderItemNotFoundException;
import com.ClinicaDeYmid.billing_service.module.sale.dto.SaleOrderItemRequestDto;
import com.ClinicaDeYmid.billing_service.module.sale.dto.SaleOrderItemResponseDto;
import com.ClinicaDeYmid.billing_service.module.sale.dto.SaleOrderResponseDto;
import com.ClinicaDeYmid.billing_service.module.sale.entity.SaleOrder;
import com.ClinicaDeYmid.billing_service.module.sale.entity.SaleOrderItem;
import com.ClinicaDeYmid.billing_service.module.sale.mapper.SaleOrderItemMapper;
import com.ClinicaDeYmid.billing_service.module.sale.mapper.SaleOrderMapper;
import com.ClinicaDeYmid.billing_service.module.sale.repository.SaleOrderItemRepository;
import com.ClinicaDeYmid.billing_service.module.sale.repository.SaleOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        SaleOrderItem item = getOrThrow(itemId);

        if (dto.portfolioId() != null
                && !dto.portfolioId().equals(item.getPortfolioId())
                && itemRepository.existsBySaleOrderIdAndPortfolioId(saleOrderId, dto.portfolioId())) {
            throw new IllegalStateException(
                    "El portafolio " + dto.portfolioId() + " ya está en la orden " + saleOrderId + ".");
        }

        try {
            itemMapper.updateEntity(dto, item);
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
        SaleOrderItem item = getOrThrow(itemId);

        order.getItems().remove(item);
        order.recalculateTotals();

        SaleOrder saved = orderRepository.save(order);
        log.info("Ítem ID: {} eliminado. Nuevo total: {}", itemId, saved.getTotalAmount());
        return orderMapper.toResponseDto(saved);
    }

    @Transactional
    public SaleOrderItemResponseDto authorizeItem(Long itemId, Long authorizationId) {
        log.info("Autorizando ítem ID: {} con autorización ID: {}", itemId, authorizationId);
        SaleOrderItem item = getOrThrow(itemId);
        item.setAuthorized(true);
        item.setAuthorizationId(authorizationId);
        return itemMapper.toResponseDto(itemRepository.save(item));
    }

    SaleOrderItem getOrThrow(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new SaleOrderItemNotFoundException(id));
    }
}
