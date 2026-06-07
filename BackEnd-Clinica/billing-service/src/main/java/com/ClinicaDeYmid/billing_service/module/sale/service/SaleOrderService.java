package com.ClinicaDeYmid.billing_service.module.sale.service;

import com.ClinicaDeYmid.billing_service.infra.exception.AttentionAlreadyHasDraftException;
import com.ClinicaDeYmid.billing_service.infra.exception.SaleOrderNotFoundException;
import com.ClinicaDeYmid.billing_service.infra.exception.SaleOrderNotEditableException;
import com.ClinicaDeYmid.billing_service.infra.feignclient.AdmissionsAttentionClient;
import com.ClinicaDeYmid.billing_service.infra.security.UserContextHolder;
import feign.FeignException;
import com.ClinicaDeYmid.billing_service.module.sale.dto.SaleOrderRequestDto;
import com.ClinicaDeYmid.billing_service.module.sale.dto.SaleOrderResponseDto;
import com.ClinicaDeYmid.billing_service.module.sale.dto.SaleOrderSummaryDto;
import com.ClinicaDeYmid.billing_service.module.sale.entity.SaleOrder;
import com.ClinicaDeYmid.billing_service.module.sale.enums.SaleOrderStatus;
import com.ClinicaDeYmid.billing_service.module.sale.mapper.SaleOrderMapper;
import com.ClinicaDeYmid.billing_service.module.sale.repository.SaleOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaleOrderService {

    private final SaleOrderRepository repository;
    private final SaleOrderMapper mapper;
    private final AdmissionsAttentionClient admissionsClient;

    @Transactional(readOnly = true)
    public SaleOrderResponseDto findById(Long id) {
        log.info("Consultando orden de venta ID: {}", id);
        return mapper.toResponseDto(getWithItemsOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<SaleOrderSummaryDto> findByAttention(Long attentionId) {
        log.info("Consultando órdenes de la atención ID: {}", attentionId);
        return repository.findByAttentionIdOrderByCreatedAtDesc(attentionId)
                .stream()
                .map(mapper::toSummaryDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<SaleOrderSummaryDto> findByStatus(SaleOrderStatus status, Pageable pageable) {
        log.info("Consultando órdenes en estado: {}", status);
        return repository.findByStatusOrderByCreatedAtDesc(status, pageable)
                .map(mapper::toSummaryDto);
    }

    @Transactional(readOnly = true)
    public Page<SaleOrderSummaryDto> findByPatient(String patientId, Pageable pageable) {
        log.info("Consultando órdenes del paciente: {}", patientId);
        return repository.findByPatientIdOrderByCreatedAtDesc(patientId, pageable)
                .map(mapper::toSummaryDto);
    }

    @Transactional
    public SaleOrderResponseDto create(SaleOrderRequestDto dto) {
        log.info("Creando orden de venta para atención ID: {}", dto.attentionId());

        if (repository.existsByAttentionIdAndStatus(dto.attentionId(), SaleOrderStatus.DRAFT)) {
            log.warn("Atención {} ya tiene un borrador activo", dto.attentionId());
            throw new AttentionAlreadyHasDraftException(dto.attentionId());
        }

        try {
            SaleOrder order = mapper.toEntity(dto);
            order.setStatus(SaleOrderStatus.DRAFT);
            order.setCreatedBy(UserContextHolder.getCurrentUserId());
            order.setUpdatedBy(UserContextHolder.getCurrentUserId());

            SaleOrder saved = repository.save(order);
            log.info("Orden de venta creada con ID: {}", saved.getId());
            return mapper.toResponseDto(saved);
        } catch (DataAccessException ex) {
            log.error("Error al crear orden de venta para atención: {}", dto.attentionId(), ex);
            throw ex;
        }
    }

    @Transactional
    public SaleOrderResponseDto update(Long id, SaleOrderRequestDto dto) {
        log.info("Actualizando orden de venta ID: {}", id);
        SaleOrder order = getOrThrow(id);
        requireDraft(order);

        try {
            mapper.updateEntity(dto, order);
            order.setUpdatedBy(UserContextHolder.getCurrentUserId());
            return mapper.toResponseDto(repository.save(order));
        } catch (DataAccessException ex) {
            log.error("Error al actualizar orden de venta ID: {}", id, ex);
            throw ex;
        }
    }

    @Transactional
    public SaleOrderResponseDto confirm(Long id) {
        log.info("Confirmando orden de venta ID: {}", id);
        SaleOrder order = getWithItemsOrThrow(id);

        if (!order.canBeConfirmed()) {
            throw new IllegalStateException(
                    "La orden " + id + " no puede confirmarse: debe estar en DRAFT y tener al menos un ítem.");
        }

        order.setStatus(SaleOrderStatus.CONFIRMED);
        order.setUpdatedBy(UserContextHolder.getCurrentUserId());
        SaleOrder saved = repository.save(order);

        try {
            admissionsClient.markAsInvoiced(saved.getAttentionId(), saved.getId());
            log.info("Atención ID: {} marcada como facturada en admissions-service", saved.getAttentionId());
        } catch (FeignException e) {
            log.error("Error al notificar admissions-service sobre orden confirmada ID: {} — {}",
                    id, e.getMessage());
        }

        log.info("Orden ID: {} confirmada. Total: {}", id, saved.getTotalAmount());
        return mapper.toResponseDto(saved);
    }

    @Transactional
    public SaleOrderResponseDto cancel(Long id) {
        log.info("Cancelando orden de venta ID: {}", id);
        SaleOrder order = getOrThrow(id);

        if (!order.canBeCancelled()) {
            throw new IllegalStateException(
                    "La orden " + id + " no puede cancelarse porque ya está facturada (INVOICED).");
        }

        order.setStatus(SaleOrderStatus.CANCELLED);
        order.setUpdatedBy(UserContextHolder.getCurrentUserId());
        SaleOrder saved = repository.save(order);

        log.info("Orden ID: {} cancelada", id);
        return mapper.toResponseDto(saved);
    }

    @Transactional
    public SaleOrderResponseDto setCopayment(Long id, BigDecimal copaymentAmount) {
        log.info("Actualizando copago de orden ID: {} → {}", id, copaymentAmount);
        SaleOrder order = getWithItemsOrThrow(id);
        requireDraft(order);

        order.setCopaymentAmount(copaymentAmount);
        order.recalculateTotals();
        order.setUpdatedBy(UserContextHolder.getCurrentUserId());

        return mapper.toResponseDto(repository.save(order));
    }

    SaleOrder getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new SaleOrderNotFoundException(id));
    }

    SaleOrder getWithItemsOrThrow(Long id) {
        return repository.findByIdWithItems(id)
                .orElseThrow(() -> new SaleOrderNotFoundException(id));
    }

    void requireDraft(SaleOrder order) {
        if (!order.isDraft()) {
            throw new SaleOrderNotEditableException(order.getId(), order.getStatus());
        }
    }
}
