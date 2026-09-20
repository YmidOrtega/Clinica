package com.ClinicaDeYmid.practitioners_service.service;

import com.ClinicaDeYmid.commons.web.EntityTags;
import com.ClinicaDeYmid.practitioners_service.repository.PractitionerHistoryRepository;
import com.ClinicaDeYmid.practitioners_service.repository.PractitionerRepository;
import com.ClinicaDeYmid.practitioners_service.repository.SpecialtyRepository;
import com.ClinicaDeYmid.practitioners_service.repository.SubSpecialtyRepository;
import com.ClinicaDeYmid.practitioners_service.repository.entity.ContactInfo;
import com.ClinicaDeYmid.practitioners_service.repository.entity.IdentityDocument;
import com.ClinicaDeYmid.practitioners_service.repository.entity.Practitioner;
import com.ClinicaDeYmid.practitioners_service.repository.entity.ProfessionalRegistration;
import com.ClinicaDeYmid.practitioners_service.shared.RelationshipType;
import com.ClinicaDeYmid.practitioners_service.repository.entity.Specialty;
import com.ClinicaDeYmid.practitioners_service.repository.entity.SubSpecialty;
import com.ClinicaDeYmid.practitioners_service.service.PractitionerCommands.Assignments;
import com.ClinicaDeYmid.practitioners_service.service.PractitionerCommands.Identity;
import com.ClinicaDeYmid.practitioners_service.service.PractitionerCommands.NewPractitioner;
import com.ClinicaDeYmid.practitioners_service.service.PractitionerCommands.Search;
import com.ClinicaDeYmid.practitioners_service.service.PractitionerCommands.SpecialtyAssignment;
import com.ClinicaDeYmid.practitioners_service.service.PractitionerViews.PractitionerView;
import com.ClinicaDeYmid.practitioners_service.service.PractitionerViews.RevisionView;
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
public class PractitionerService {

    private static final Logger log = LoggerFactory.getLogger(PractitionerService.class);

    private final PractitionerRepository practitioners;
    private final SpecialtyRepository specialties;
    private final SubSpecialtyRepository subSpecialties;
    private final PractitionerHistoryRepository history;
    private final Clock clock;

    public PractitionerService(PractitionerRepository practitioners, SpecialtyRepository specialties,
                               SubSpecialtyRepository subSpecialties, PractitionerHistoryRepository history,
                               Clock clock) {
        this.practitioners = practitioners;
        this.specialties = specialties;
        this.subSpecialties = subSpecialties;
        this.history = history;
        this.clock = clock;
    }

    @Transactional
    public PractitionerView register(NewPractitioner command) {
        IdentityDocument document = document(command.document());
        ProfessionalRegistration registration = registration(command.registration());
        ContactInfo contact = contact(command.contact());
        refuseRepeatedDocument(document, null);
        refuseRepeatedRegistration(registration.number(), null);
        refuseRepeatedEmail(contact.email(), null);
        Practitioner registered = practitioners.saveAndFlush(Practitioner.register(document, command.firstNames(),
                command.lastNames(), registration, contact, relationship(command.relationship())));
        log.info("Practitioner registered: uuid={} registration={}", registered.uuid(), registration.number());
        return PractitionerView.of(registered);
    }

    @Transactional
    public PractitionerView correctIdentity(UUID uuid, long expectedVersion, Identity command) {
        IdentityDocument document = document(command.document());
        return change(uuid, expectedVersion, practitioner -> {
            refuseRepeatedDocument(document, practitioner.uuid());
            practitioner.correctIdentity(document, command.firstNames(), command.lastNames());
        });
    }

    @Transactional
    public PractitionerView correctRegistration(UUID uuid, long expectedVersion,
                                                PractitionerCommands.Registration command) {
        ProfessionalRegistration registration = registration(command);
        return change(uuid, expectedVersion, practitioner -> {
            refuseRepeatedRegistration(registration.number(), practitioner.uuid());
            practitioner.correctRegistration(registration);
        });
    }

    @Transactional
    public PractitionerView correctContact(UUID uuid, long expectedVersion, PractitionerCommands.Contact command) {
        ContactInfo contact = contact(command);
        return change(uuid, expectedVersion, practitioner -> {
            refuseRepeatedEmail(contact.email(), practitioner.uuid());
            practitioner.correctContact(contact);
        });
    }

    @Transactional
    public PractitionerView agreeRelationship(UUID uuid, long expectedVersion, RelationshipType relationship) {
        return change(uuid, expectedVersion, practitioner -> practitioner.agreeRelationship(relationship(relationship)));
    }

    @Transactional
    public PractitionerView assignSpecialties(UUID uuid, long expectedVersion, Assignments command) {
        return change(uuid, expectedVersion, practitioner -> practitioner.assign(resolve(practitioner, command), clock));
    }

    @Transactional
    public PractitionerView suspend(UUID uuid, long expectedVersion, String reason) {
        return change(uuid, expectedVersion, practitioner -> practitioner.suspend(reason, clock));
    }

    @Transactional
    public PractitionerView retire(UUID uuid, long expectedVersion, String reason) {
        return change(uuid, expectedVersion, practitioner -> practitioner.retire(reason, clock));
    }

    @Transactional
    public PractitionerView reinstate(UUID uuid, long expectedVersion) {
        return change(uuid, expectedVersion, Practitioner::reinstate);
    }

    @Transactional(readOnly = true)
    public PractitionerView get(UUID uuid) {
        return PractitionerView.of(practitioners.findByUuid(uuid)
                .orElseThrow(PractitionersException.PractitionerNotFound::new));
    }

    @Transactional(readOnly = true)
    public List<PractitionerView> search(Search command) {
        if (command.document() != null) {
            IdentityDocument document = document(command.document());
            return practitioners.findByDocument(document.type(), document.number()).stream()
                    .map(found -> get(found.uuid()))
                    .toList();
        }
        if (command.registrationNumber() != null) {
            return practitioners.findByRegistrationNumber(Rules.upper(command.registrationNumber())).stream()
                    .map(found -> get(found.uuid()))
                    .toList();
        }
        return practitioners.search(command.status(), Rules.optionalText(command.lastNames(), "lastNames", 100),
                        Rules.upper(command.specialtyCode())).stream()
                .map(PractitionerView::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RevisionView> history(UUID uuid) {
        practitioners.findByUuid(uuid).orElseThrow(PractitionersException.PractitionerNotFound::new);
        return history.of(uuid).stream().map(RevisionView::of).toList();
    }

    private List<Practitioner.Assignment> resolve(Practitioner practitioner, Assignments command) {
        return command.specialties().stream().map(assignment -> {
            Specialty specialty = specialties.findByCode(Rules.upper(assignment.specialtyCode()))
                    .orElseThrow(PractitionersException.SpecialtyNotFound::new);
            SubSpecialty subSpecialty = assignment.subSpecialtyCode() == null ? null
                    : subSpecialties.findByCode(Rules.upper(assignment.subSpecialtyCode()))
                    .orElseThrow(PractitionersException.SubSpecialtyNotFound::new);
            return new Practitioner.Assignment(specialty, subSpecialty, assignment.principal());
        }).toList();
    }

    private PractitionerView change(UUID uuid, long expectedVersion, Consumer<Practitioner> change) {
        Practitioner practitioner = practitioners.findByUuid(uuid)
                .orElseThrow(PractitionersException.PractitionerNotFound::new);
        if (practitioner.version() != expectedVersion) {
            throw new EntityTags.StaleVersion();
        }
        change.accept(practitioner);
        return PractitionerView.of(practitioners.saveAndFlush(practitioner));
    }

    private void refuseRepeatedDocument(IdentityDocument document, UUID allowed) {
        refuseRepeated(practitioners.findByDocument(document.type(), document.number()), allowed,
                PractitionersException.DocumentAlreadyRegistered::new);
    }

    private void refuseRepeatedRegistration(String number, UUID allowed) {
        refuseRepeated(practitioners.findByRegistrationNumber(number), allowed,
                PractitionersException.RegistrationAlreadyUsed::new);
    }

    private void refuseRepeatedEmail(String email, UUID allowed) {
        refuseRepeated(practitioners.findByEmail(email), allowed, PractitionersException.EmailAlreadyUsed::new);
    }

    private void refuseRepeated(Optional<Practitioner> found, UUID allowed,
                                java.util.function.Supplier<RuntimeException> conflict) {
        found.filter(other -> allowed == null || !other.uuid().equals(allowed))
                .ifPresent(other -> {
                    throw conflict.get();
                });
    }

    private IdentityDocument document(PractitionerCommands.Document command) {
        Rules.required(command, "document");
        return new IdentityDocument(command.type(), command.number());
    }

    private ProfessionalRegistration registration(PractitionerCommands.Registration command) {
        Rules.required(command, "registration");
        return new ProfessionalRegistration(command.number(), command.registeredOn(), clock);
    }

    private ContactInfo contact(PractitionerCommands.Contact command) {
        Rules.required(command, "contact");
        return new ContactInfo(command.email(), command.mobile(), command.phone());
    }

    private RelationshipType relationship(RelationshipType relationship) {
        return Rules.required(relationship, "relationship");
    }
}
