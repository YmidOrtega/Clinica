package com.ClinicaDeYmid.admissions_service.application;

import com.ClinicaDeYmid.admissions_service.domain.AdmissionsException;
import com.ClinicaDeYmid.admissions_service.domain.CareType;
import com.ClinicaDeYmid.admissions_service.domain.CareTypes;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationServices;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.Locations;
import com.ClinicaDeYmid.admissions_service.domain.ServiceType;
import com.ClinicaDeYmid.admissions_service.domain.ServiceTypes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CatalogueQueries {

    private final ServiceTypes serviceTypes;
    private final CareTypes careTypes;
    private final Locations locations;
    private final ConfigurationServices configurationServices;

    public CatalogueQueries(ServiceTypes serviceTypes, CareTypes careTypes, Locations locations,
                            ConfigurationServices configurationServices) {
        this.serviceTypes = serviceTypes;
        this.careTypes = careTypes;
        this.locations = locations;
        this.configurationServices = configurationServices;
    }

    public ServiceType serviceType(UUID uuid) {
        return serviceTypes.findByUuid(uuid).orElseThrow(AdmissionsException.ServiceTypeNotFound::new);
    }

    public List<ServiceType> serviceTypes() {
        return serviceTypes.findAll();
    }

    public CareType careType(UUID uuid) {
        return careTypes.findByUuid(uuid).orElseThrow(AdmissionsException.CareTypeNotFound::new);
    }

    public List<CareType> careTypesOf(UUID serviceTypeUuid) {
        return careTypes.findByServiceType(serviceTypeUuid);
    }

    public Location location(UUID uuid) {
        return locations.findByUuid(uuid).orElseThrow(AdmissionsException.LocationNotFound::new);
    }

    public List<Location> locations() {
        return locations.findAll();
    }

    public ConfigurationService configuration(UUID uuid) {
        return configurationServices.findByUuid(uuid)
                .orElseThrow(AdmissionsException.ConfigurationServiceNotFound::new);
    }

    public List<ConfigurationService> configurations() {
        return configurationServices.findAll();
    }
}
