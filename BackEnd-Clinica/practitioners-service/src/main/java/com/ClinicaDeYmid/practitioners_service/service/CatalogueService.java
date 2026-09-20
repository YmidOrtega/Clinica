package com.ClinicaDeYmid.practitioners_service.service;

import com.ClinicaDeYmid.commons.web.EntityTags;
import com.ClinicaDeYmid.practitioners_service.repository.SpecialtyRepository;
import com.ClinicaDeYmid.practitioners_service.repository.SubSpecialtyRepository;
import com.ClinicaDeYmid.practitioners_service.repository.entity.CatalogueStatus;
import com.ClinicaDeYmid.practitioners_service.repository.entity.Specialty;
import com.ClinicaDeYmid.practitioners_service.repository.entity.SubSpecialty;
import com.ClinicaDeYmid.practitioners_service.service.CatalogueCommands.CatalogueEntry;
import com.ClinicaDeYmid.practitioners_service.service.CatalogueCommands.NewSpecialty;
import com.ClinicaDeYmid.practitioners_service.service.CatalogueCommands.NewSubSpecialty;
import com.ClinicaDeYmid.practitioners_service.service.CatalogueViews.ImportSummary;
import com.ClinicaDeYmid.practitioners_service.service.CatalogueViews.SpecialtyView;
import com.ClinicaDeYmid.practitioners_service.service.CatalogueViews.SubSpecialtyView;
import com.ClinicaDeYmid.practitioners_service.shared.PractitionersException;
import com.ClinicaDeYmid.practitioners_service.shared.Rules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class CatalogueService {

    private static final Logger log = LoggerFactory.getLogger(CatalogueService.class);

    private final SpecialtyRepository specialties;
    private final SubSpecialtyRepository subSpecialties;
    private final Clock clock;

    public CatalogueService(SpecialtyRepository specialties, SubSpecialtyRepository subSpecialties, Clock clock) {
        this.specialties = specialties;
        this.subSpecialties = subSpecialties;
        this.clock = clock;
    }

    @Transactional
    public SpecialtyView register(NewSpecialty command) {
        String code = Rules.upper(command.code());
        if (specialties.findByCode(code).isPresent()) {
            throw new PractitionersException.SpecialtyCodeAlreadyUsed();
        }
        Specialty registered = specialties.saveAndFlush(Specialty.register(command.code(), command.name()));
        log.info("Specialty registered: uuid={} code={}", registered.uuid(), registered.code());
        return SpecialtyView.of(registered);
    }

    @Transactional
    public SpecialtyView rename(UUID uuid, long expectedVersion, String name) {
        return changeSpecialty(uuid, expectedVersion, specialty -> specialty.rename(name));
    }

    @Transactional
    public SpecialtyView deactivate(UUID uuid, long expectedVersion, String reason) {
        return changeSpecialty(uuid, expectedVersion, specialty -> specialty.deactivate(reason, clock));
    }

    @Transactional
    public SpecialtyView reactivate(UUID uuid, long expectedVersion) {
        return changeSpecialty(uuid, expectedVersion, Specialty::reactivate);
    }

    @Transactional
    public SubSpecialtyView addSubSpecialty(UUID specialtyUuid, NewSubSpecialty command) {
        Specialty specialty = specialties.findByUuid(specialtyUuid)
                .orElseThrow(PractitionersException.SpecialtyNotFound::new);
        if (subSpecialties.findByCode(Rules.upper(command.code())).isPresent()) {
            throw new PractitionersException.SubSpecialtyCodeAlreadyUsed();
        }
        SubSpecialty added = subSpecialties.saveAndFlush(specialty.add(command.code(), command.name()));
        log.info("Sub-specialty registered: uuid={} code={} specialty={}", added.uuid(), added.code(), specialty.code());
        return SubSpecialtyView.of(added);
    }

    @Transactional
    public SubSpecialtyView renameSubSpecialty(UUID uuid, long expectedVersion, String name) {
        return changeSubSpecialty(uuid, expectedVersion, subSpecialty -> subSpecialty.rename(name));
    }

    @Transactional
    public SubSpecialtyView deactivateSubSpecialty(UUID uuid, long expectedVersion, String reason) {
        return changeSubSpecialty(uuid, expectedVersion, subSpecialty -> subSpecialty.deactivate(reason, clock));
    }

    @Transactional
    public SubSpecialtyView reactivateSubSpecialty(UUID uuid, long expectedVersion) {
        return changeSubSpecialty(uuid, expectedVersion, SubSpecialty::reactivate);
    }

    @Transactional
    public ImportSummary importCatalogue(List<CatalogueEntry> entries) {
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int subCreated = 0;
        int subUpdated = 0;
        for (CatalogueEntry entry : entries) {
            Specialty specialty = specialties.findByCode(Rules.upper(entry.code())).orElse(null);
            if (specialty == null) {
                specialty = specialties.save(Specialty.register(entry.code(), entry.name()));
                created++;
            } else if (specialty.rename(entry.name())) {
                specialties.save(specialty);
                updated++;
            } else {
                unchanged++;
            }
            for (NewSubSpecialty sub : entry.subSpecialties()) {
                Optional<SubSpecialty> existing = subSpecialties.findByCode(Rules.upper(sub.code()));
                if (existing.isEmpty()) {
                    subSpecialties.save(specialty.add(sub.code(), sub.name()));
                    subCreated++;
                } else if (existing.get().rename(sub.name())) {
                    subSpecialties.save(existing.get());
                    subUpdated++;
                }
            }
        }
        ImportSummary summary = new ImportSummary(created, updated, unchanged, subCreated, subUpdated);
        log.info("Catalogue import: created={} updated={} unchanged={} subCreated={} subUpdated={}",
                created, updated, unchanged, subCreated, subUpdated);
        return summary;
    }

    @Transactional(readOnly = true)
    public SpecialtyView get(UUID uuid) {
        return SpecialtyView.of(specialties.findByUuid(uuid).orElseThrow(PractitionersException.SpecialtyNotFound::new));
    }

    @Transactional(readOnly = true)
    public List<SpecialtyView> search(CatalogueCommands.StatusFilter status, String name) {
        CatalogueStatus.Code code = status == null ? null : CatalogueStatus.Code.valueOf(status.name());
        return specialties.search(code, Rules.optionalText(name, "name", 150)).stream()
                .map(SpecialtyView::of)
                .toList();
    }

    private SpecialtyView changeSpecialty(UUID uuid, long expectedVersion, Consumer<Specialty> change) {
        Specialty specialty = specialties.findByUuid(uuid).orElseThrow(PractitionersException.SpecialtyNotFound::new);
        if (specialty.version() != expectedVersion) {
            throw new EntityTags.StaleVersion();
        }
        change.accept(specialty);
        return SpecialtyView.of(specialties.saveAndFlush(specialty));
    }

    private SubSpecialtyView changeSubSpecialty(UUID uuid, long expectedVersion, Consumer<SubSpecialty> change) {
        SubSpecialty subSpecialty = subSpecialties.findByUuid(uuid)
                .orElseThrow(PractitionersException.SubSpecialtyNotFound::new);
        if (subSpecialty.version() != expectedVersion) {
            throw new EntityTags.StaleVersion();
        }
        change.accept(subSpecialty);
        return SubSpecialtyView.of(subSpecialties.saveAndFlush(subSpecialty));
    }
}
