package com.ClinicaDeYmid.admissions_service.application;

import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionsException;
import com.ClinicaDeYmid.admissions_service.domain.CareType;
import com.ClinicaDeYmid.admissions_service.domain.CareTypes;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationServices;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.Locations;
import com.ClinicaDeYmid.admissions_service.domain.ServiceType;
import com.ClinicaDeYmid.admissions_service.domain.ServiceTypes;
import com.ClinicaDeYmid.commons.web.EntityTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class CatalogueCommands {

    private static final Logger log = LoggerFactory.getLogger(CatalogueCommands.class);

    private final ServiceTypes serviceTypes;
    private final CareTypes careTypes;
    private final Locations locations;
    private final ConfigurationServices configurationServices;
    private final TransactionOperations transactions;
    private final Clock clock;

    public CatalogueCommands(ServiceTypes serviceTypes, CareTypes careTypes, Locations locations,
                             ConfigurationServices configurationServices, TransactionOperations transactions,
                             Clock clock) {
        this.serviceTypes = serviceTypes;
        this.careTypes = careTypes;
        this.locations = locations;
        this.configurationServices = configurationServices;
        this.transactions = transactions;
        this.clock = clock;
    }

    public ServiceType defineServiceType(String name, AdmissionKind kind) {
        return transactions.execute(status -> {
            serviceTypes.findByName(name).ifPresent(other -> {
                throw new AdmissionsException.NameAlreadyUsed("los tipos de servicio");
            });
            ServiceType defined = serviceTypes.save(ServiceType.define(name, kind));
            log.info("Service type defined: uuid={} kind={}", defined.uuid(), kind);
            return defined;
        });
    }

    public ServiceType renameServiceType(String name, UUID uuid, long expectedVersion) {
        return modifyServiceType(uuid, expectedVersion, serviceType -> {
            serviceTypes.findByName(name)
                    .filter(other -> !other.uuid().equals(uuid))
                    .ifPresent(other -> {
                        throw new AdmissionsException.NameAlreadyUsed("los tipos de servicio");
                    });
            serviceType.rename(name);
        });
    }

    public ServiceType retireServiceType(UUID uuid, long expectedVersion, String reason) {
        return modifyServiceType(uuid, expectedVersion, serviceType -> serviceType.retire(reason, clock));
    }

    public ServiceType restoreServiceType(UUID uuid, long expectedVersion) {
        return modifyServiceType(uuid, expectedVersion, ServiceType::restore);
    }

    public Location defineLocation(String name) {
        return transactions.execute(status -> {
            locations.findByName(name).ifPresent(other -> {
                throw new AdmissionsException.NameAlreadyUsed("las ubicaciones");
            });
            Location defined = locations.save(Location.define(name));
            log.info("Location defined: uuid={}", defined.uuid());
            return defined;
        });
    }

    public Location renameLocation(String name, UUID uuid, long expectedVersion) {
        return modifyLocation(uuid, expectedVersion, location -> {
            locations.findByName(name)
                    .filter(other -> !other.uuid().equals(uuid))
                    .ifPresent(other -> {
                        throw new AdmissionsException.NameAlreadyUsed("las ubicaciones");
                    });
            location.rename(name);
        });
    }

    public Location retireLocation(UUID uuid, long expectedVersion, String reason) {
        return modifyLocation(uuid, expectedVersion, location -> location.retire(reason, clock));
    }

    public Location restoreLocation(UUID uuid, long expectedVersion) {
        return modifyLocation(uuid, expectedVersion, Location::restore);
    }

    public CareType defineCareType(String name, UUID serviceTypeUuid) {
        return transactions.execute(status -> {
            ServiceType serviceType = serviceTypes.findByUuid(serviceTypeUuid)
                    .orElseThrow(AdmissionsException.ServiceTypeNotFound::new);
            careTypes.findByNameAndServiceType(name, serviceTypeUuid).ifPresent(other -> {
                throw new AdmissionsException.NameAlreadyUsed("los tipos de atención de ese servicio");
            });
            CareType defined = careTypes.save(CareType.define(name, serviceType));
            log.info("Care type defined: uuid={} serviceType={}", defined.uuid(), serviceTypeUuid);
            return defined;
        });
    }

    public CareType retireCareType(UUID uuid, long expectedVersion, String reason) {
        return transactions.execute(status -> {
            CareType careType = careTypes.findByUuid(uuid).orElseThrow(AdmissionsException.CareTypeNotFound::new);
            requireVersion(careType.version(), expectedVersion);
            careType.retire(reason, clock);
            return careTypes.save(careType);
        });
    }

    public CareType restoreCareType(UUID uuid, long expectedVersion) {
        return transactions.execute(status -> {
            CareType careType = careTypes.findByUuid(uuid).orElseThrow(AdmissionsException.CareTypeNotFound::new);
            requireVersion(careType.version(), expectedVersion);
            careType.restore();
            return careTypes.save(careType);
        });
    }

    public ConfigurationService configure(UUID serviceTypeUuid, UUID locationUuid) {
        return transactions.execute(status -> {
            ServiceType serviceType = serviceTypes.findByUuid(serviceTypeUuid)
                    .orElseThrow(AdmissionsException.ServiceTypeNotFound::new);
            Location location = locations.findByUuid(locationUuid)
                    .orElseThrow(AdmissionsException.LocationNotFound::new);
            configurationServices.findByServiceTypeAndLocation(serviceTypeUuid, locationUuid).ifPresent(other -> {
                throw new AdmissionsException.ServiceAlreadyConfigured();
            });
            ConfigurationService configured =
                    configurationServices.save(ConfigurationService.configure(serviceType, location));
            log.info("Service configured: uuid={} serviceType={} location={}",
                    configured.uuid(), serviceTypeUuid, locationUuid);
            return configured;
        });
    }

    public ConfigurationService retireConfiguration(UUID uuid, long expectedVersion, String reason) {
        return modifyConfiguration(uuid, expectedVersion, configured -> configured.retire(reason, clock));
    }

    public ConfigurationService restoreConfiguration(UUID uuid, long expectedVersion) {
        return modifyConfiguration(uuid, expectedVersion, ConfigurationService::restore);
    }

    private static void requireVersion(long actual, long expected) {
        if (actual != expected) {
            throw new EntityTags.StaleVersion();
        }
    }

    private ServiceType modifyServiceType(UUID uuid, long expectedVersion, Consumer<ServiceType> change) {
        return transactions.execute(status -> {
            ServiceType serviceType = serviceTypes.findByUuid(uuid)
                    .orElseThrow(AdmissionsException.ServiceTypeNotFound::new);
            requireVersion(serviceType.version(), expectedVersion);
            change.accept(serviceType);
            return serviceTypes.save(serviceType);
        });
    }

    private Location modifyLocation(UUID uuid, long expectedVersion, Consumer<Location> change) {
        return transactions.execute(status -> {
            Location location = locations.findByUuid(uuid).orElseThrow(AdmissionsException.LocationNotFound::new);
            requireVersion(location.version(), expectedVersion);
            change.accept(location);
            return locations.save(location);
        });
    }

    private ConfigurationService modifyConfiguration(UUID uuid, long expectedVersion,
                                                     Consumer<ConfigurationService> change) {
        return transactions.execute(status -> {
            ConfigurationService configured = configurationServices.findByUuid(uuid)
                    .orElseThrow(AdmissionsException.ConfigurationServiceNotFound::new);
            requireVersion(configured.version(), expectedVersion);
            change.accept(configured);
            return configurationServices.save(configured);
        });
    }
}
