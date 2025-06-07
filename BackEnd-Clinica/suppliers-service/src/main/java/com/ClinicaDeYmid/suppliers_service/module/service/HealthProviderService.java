package com.ClinicaDeYmid.suppliers_service.module.service;

import com.ClinicaDeYmid.suppliers_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.suppliers_service.module.repository.HealthProviderRepository;
import com.ClinicaDeYmid.suppliers_service.module.enums.Status;
import com.ClinicaDeYmid.suppliers_service.module.enums.TypeProvider;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.Year;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HealthProviderService {

    /**
     * Verifica si el proveedor está activo
     * @param provider La entidad HealthProvider
     * @return true si está activo, false en caso contrario
     */
    public boolean isActive(HealthProvider provider) {
        return provider != null && Boolean.TRUE.equals(provider.getActive());
    }

    /**
     * Determina el estado del contrato basándose en los años de validez y finalización.
     * Esta es la lógica principal para el campo 'Status'
     * @param provider La entidad HealthProvider
     * @return El Status del contrato (ACTIVE, PENDING, EXPIRED, UNKNOWN)
     */
    public Status getContractStatus(HealthProvider provider) {
        if (provider == null) {
            return Status.INACTIVE; // O un estado que indique error/no definido
        }

        Integer yearOfValidity = provider.getYearOfValidity();
        Integer yearCompletion = provider.getYearCompletion();
        int currentYear = Year.now().getValue();

        // Si no hay años definidos, o si los años son inconsistentes
        if (yearOfValidity == null && yearCompletion == null) {
            return Status.PENDING; // O Status.UNKNOWN si tienes uno
        }

        // Contrato que aún no ha comenzado
        if (yearOfValidity != null && yearOfValidity > currentYear) {
            return Status.PENDING;
        }

        // Contrato vencido
        if (yearCompletion != null && yearCompletion < currentYear) {
            return Status.INACTIVE; // Usamos INACTIVE para representar "vencido"
        }

        // Si llegó hasta aquí, y no está pendiente ni vencido, está activo/vigente
        return Status.ACTIVE;
    }

    /**
     * Verifica si el contrato está vencido basándose en el año de finalización
     * @param provider La entidad HealthProvider
     * @return true si el contrato está vencido, false en caso contrario
     */
    public boolean isContractExpired(HealthProvider provider) {
        if (provider == null || provider.getYearCompletion() == null) {
            return false;
        }
        return provider.getYearCompletion() < Year.now().getValue();
    }

    /**
     * Verifica si el contrato está vigente
     * @param provider La entidad HealthProvider
     * @return true si el contrato está vigente, false en caso contrario
     */
    public boolean isContractValid(HealthProvider provider) {
        if (provider == null) {
            return false;
        }

        Integer yearOfValidity = provider.getYearOfValidity();
        Integer yearCompletion = provider.getYearCompletion();
        int currentYear = Year.now().getValue();

        if (yearOfValidity == null) {
            return true; // Si no tiene año de validez, se considera válido por defecto
        }

        if (yearOfValidity > currentYear) {
            return false; // Aún no ha comenzado
        }

        if (yearCompletion != null && yearCompletion < currentYear) {
            return false; // Ya venció
        }

        return true;
    }


    /**
     * Obtiene el NIT formateado utilizando el Value Object Nit.
     * @param provider La entidad HealthProvider
     * @return El NIT formateado
     */
    public String getFormattedNit(HealthProvider provider) {
        if (provider == null || provider.getNit() == null) {
            return null; // O una cadena vacía, dependiendo de la necesidad
        }
        return provider.getNit().getFormattedNit();
    }

    /**
     * Verifica si es un proveedor de tipo EPS
     * @param provider La entidad HealthProvider
     * @return true si es EPS, false en caso contrario
     */
    public boolean isEps(HealthProvider provider) {
        return provider != null && TypeProvider.EPS.equals(provider.getTypeProvider());
    }

    /**
     * Verifica si es un proveedor de tipo IPS
     * @param provider La entidad HealthProvider
     * @return true si es IPS, false en caso contrario
     */
    public boolean isIps(HealthProvider provider) {
        return provider != null && TypeProvider.IPS.equals(provider.getTypeProvider());
    }


}