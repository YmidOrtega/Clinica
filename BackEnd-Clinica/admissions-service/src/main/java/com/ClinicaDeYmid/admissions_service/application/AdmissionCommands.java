package com.ClinicaDeYmid.admissions_service.application;

import com.ClinicaDeYmid.admissions_service.application.coverage.CoverageGate;
import com.ClinicaDeYmid.admissions_service.application.patient.PatientDirectory;
import com.ClinicaDeYmid.admissions_service.application.practitioner.PractitionerDirectory;
import com.ClinicaDeYmid.admissions_service.application.patient.PatientReferenceProjection;
import com.ClinicaDeYmid.admissions_service.application.patient.PatientRegistry;
import com.ClinicaDeYmid.admissions_service.application.patient.UnidentifiedAdmissionRequest;
import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionPhase;
import com.ClinicaDeYmid.admissions_service.domain.Admissions;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionsException;
import com.ClinicaDeYmid.admissions_service.domain.CareType;
import com.ClinicaDeYmid.admissions_service.domain.CareTypes;
import com.ClinicaDeYmid.admissions_service.domain.Cause;
import com.ClinicaDeYmid.admissions_service.domain.Companion;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationServices;
import com.ClinicaDeYmid.admissions_service.domain.AttendingPractitioner;
import com.ClinicaDeYmid.admissions_service.domain.Coverage;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.admissions_service.domain.practitioner.PractitionerReference;
import com.ClinicaDeYmid.commons.web.EntityTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class AdmissionCommands {

    private static final Logger log = LoggerFactory.getLogger(AdmissionCommands.class);

    private final Admissions admissions;
    private final ConfigurationServices configurationServices;
    private final CareTypes careTypes;
    private final PatientDirectory patients;
    private final PatientRegistry registry;
    private final CoverageGate coverage;
    private final BedAssignments bedAssignments;
    private final PractitionerDirectory practitioners;
    private final PatientReferenceProjection projection;
    private final TransactionOperations transactions;
    private final Clock clock;

    public AdmissionCommands(Admissions admissions, ConfigurationServices configurationServices, CareTypes careTypes,
                             PatientDirectory patients, PatientRegistry registry, CoverageGate coverage,
                             BedAssignments bedAssignments, PractitionerDirectory practitioners,
                             PatientReferenceProjection projection, TransactionOperations transactions, Clock clock) {
        this.admissions = admissions;
        this.configurationServices = configurationServices;
        this.careTypes = careTypes;
        this.patients = patients;
        this.registry = registry;
        this.coverage = coverage;
        this.bedAssignments = bedAssignments;
        this.practitioners = practitioners;
        this.projection = projection;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Admission register(UUID patientUuid, UUID configurationServiceUuid, Cause cause, UUID careTypeUuid,
                              Companion companion) {
        return register(patientUuid, configurationServiceUuid, cause, careTypeUuid, companion, false);
    }

    public Admission register(UUID patientUuid, UUID configurationServiceUuid, Cause cause, UUID careTypeUuid,
                              Companion companion, boolean overrideCoverage) {
        PatientReference patient = patients.require(patientUuid);
        if (!patient.admissible()) {
            throw new AdmissionsException.PatientNotAdmissible();
        }
        ConfigurationService service = transactions.execute(status -> configurationServices
                .findByUuid(configurationServiceUuid)
                .orElseThrow(AdmissionsException.ConfigurationServiceNotFound::new));
        Coverage assessed = coverage.assess(patient, service.kind(), overrideCoverage);
        return transactions.execute(status -> {
            ConfigurationService attached = configurationServices.findByUuid(configurationServiceUuid)
                    .orElseThrow(AdmissionsException.ConfigurationServiceNotFound::new);
            CareType careType = careTypeUuid == null ? null : careTypes.findByUuid(careTypeUuid)
                    .orElseThrow(AdmissionsException.CareTypeNotFound::new);
            String number = admissions.nextNumber(LocalDate.now(clock).getYear());
            Admission registered = Admission.register(number, patientUuid, attached, cause, careType, companion, clock);
            registered.assess(assessed);
            admissions.save(registered);
            log.info("Admission registered: number={} patient={} kind={} coverage={}",
                    registered.number(), patientUuid, registered.kind(), assessed.status());
            return reloaded(registered.uuid());
        });
    }

    public Admission admitUnidentified(UnidentifiedAdmissionRequest request, UUID configurationServiceUuid,
                                       Cause cause, UUID careTypeUuid, Companion companion) {
        PatientReference.Unidentified registered =
                registry.registerUnidentified(request.sex(), request.estimatedBirthYear(), request.description());
        projection.apply(registered);
        log.info("Unidentified patient {} registered in patient-service before admitting", registered.code());
        return register(registered.uuid(), configurationServiceUuid, cause, careTypeUuid, companion);
    }

    public Admission activate(UUID uuid, long expectedVersion) {
        return modify(uuid, expectedVersion, admission -> admission.activate(clock));
    }

    public Admission cancel(UUID uuid, long expectedVersion, String reason) {
        return modify(uuid, expectedVersion, admission -> {
            if (admission.occupiesABed()) {
                bedAssignments.freeCurrentBed(admission);
            }
            admission.cancel(reason, clock);
        });
    }

    public Admission discharge(UUID uuid, long expectedVersion) {
        return modify(uuid, expectedVersion, admission -> {
            if (admission.occupiesABed()) {
                bedAssignments.freeCurrentBed(admission);
            }
            admission.discharge(clock);
        });
    }

    public Admission moveTo(UUID uuid, long expectedVersion, UUID configurationServiceUuid, String reason,
                            UUID bedUuid) {
        Admission moved = transactions.execute(status -> {
            Admission admission = admissions.findByUuid(uuid).orElseThrow(AdmissionsException.AdmissionNotFound::new);
            requireVersion(admission.version(), expectedVersion);
            ConfigurationService service = configurationServices.findByUuid(configurationServiceUuid)
                    .orElseThrow(AdmissionsException.ConfigurationServiceNotFound::new);
            if (service.kind().bedRequired() && bedUuid == null && !admission.occupiesABed()) {
                throw new AdmissionsException.BedRequired();
            }
            if (!service.kind().bedRequired() && admission.occupiesABed()) {
                bedAssignments.freeCurrentBed(admission);
            }
            AdmissionPhase phase = admission.moveTo(service, reason, clock);
            admissions.save(admission);
            log.info("Admission {} moved to {} ({})", admission.number(), service.uuid(), phase.kind());
            return reloaded(uuid);
        });
        return bedUuid == null ? moved : bedAssignments.assign(uuid, moved.version(), bedUuid);
    }

    public Admission accompaniedBy(UUID uuid, long expectedVersion, Companion companion) {
        return modify(uuid, expectedVersion, admission -> admission.accompaniedBy(companion));
    }

    public Admission attendedBy(UUID uuid, long expectedVersion, UUID practitionerUuid) {
        PractitionerReference practitioner = practitioners.require(practitionerUuid);
        return modify(uuid, expectedVersion, admission -> admission.attendedBy(AttendingPractitioner.of(
                practitioner.practitionerUuid(), practitioner.fullName(), practitioner.registrationNumber())));
    }

    private Admission modify(UUID uuid, long expectedVersion, Consumer<Admission> change) {
        return transactions.execute(status -> {
            Admission admission = admissions.findByUuid(uuid).orElseThrow(AdmissionsException.AdmissionNotFound::new);
            requireVersion(admission.version(), expectedVersion);
            change.accept(admission);
            admissions.save(admission);
            return reloaded(uuid);
        });
    }

    private Admission reloaded(UUID uuid) {
        return admissions.findByUuid(uuid).orElseThrow(AdmissionsException.AdmissionNotFound::new);
    }

    private static void requireVersion(long actual, long expected) {
        if (actual != expected) {
            throw new EntityTags.StaleVersion();
        }
    }
}
