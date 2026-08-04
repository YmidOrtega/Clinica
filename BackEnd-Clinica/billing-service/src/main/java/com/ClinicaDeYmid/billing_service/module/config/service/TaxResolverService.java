package com.ClinicaDeYmid.billing_service.module.config.service;

import com.ClinicaDeYmid.billing_service.module.config.entity.TaxConfiguration;
import com.ClinicaDeYmid.billing_service.module.config.enums.TaxType;
import com.ClinicaDeYmid.billing_service.module.config.repository.TaxConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Resuelve la tasa de impuesto aplicable a una línea de venta a partir de
 * {@code tax_configuration}.
 * <p>
 * Solo se consideran los impuestos <b>aditivos</b> (IVA, INC), que incrementan el valor
 * a cobrar. Las retenciones (RETEFUENTE, RETEIVA) se excluyen deliberadamente: son
 * descuentos sobre el pago, no cargos sobre la línea, y sumarlas al {@code taxRate} del
 * ítem inflaría el total. Su tratamiento corresponde al módulo {@code invoice} (Fase 2),
 * donde pueden modelarse como deducciones a nivel de factura.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxResolverService {

    private static final Set<TaxType> ADDITIVE_TAXES = Set.of(TaxType.IVA, TaxType.INC);

    private final TaxConfigurationRepository taxRepository;

    /**
     * Tasa aditiva total aplicable a servicios (exámenes, procedimientos, honorarios,
     * habitación y otros conceptos).
     */
    @Transactional(readOnly = true)
    public BigDecimal resolveRateForServices() {
        return sumAdditive(taxRepository.findActiveForServices(), "servicios");
    }

    /**
     * Tasa aditiva total aplicable a medicamentos e insumos.
     */
    @Transactional(readOnly = true)
    public BigDecimal resolveRateForMedications() {
        return sumAdditive(taxRepository.findActiveForMedications(), "medicamentos e insumos");
    }

    private BigDecimal sumAdditive(List<TaxConfiguration> taxes, String target) {
        BigDecimal rate = taxes.stream()
                .filter(t -> ADDITIVE_TAXES.contains(t.getType()))
                .map(TaxConfiguration::getPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.debug("Tasa de impuesto aditiva para {}: {}%", target, rate);
        return rate;
    }
}
