package com.ClinicaDeYmid.clinical_history_service.application.copy;

import com.ClinicaDeYmid.clinical_history_service.application.access.AccessAudit;
import com.ClinicaDeYmid.clinical_history_service.application.access.AccessEvent;
import com.ClinicaDeYmid.clinical_history_service.application.integrity.IntegrityQueries;
import com.ClinicaDeYmid.clinical_history_service.application.patient.PatientDirectory;
import com.ClinicaDeYmid.clinical_history_service.application.patient.PatientLookup;
import com.ClinicaDeYmid.clinical_history_service.application.record.ClinicalRecordQueries.EncounterRecord;
import com.ClinicaDeYmid.clinical_history_service.application.record.ClinicalRecordQueries.NoteEntry;
import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessAction;
import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessBasis;
import com.ClinicaDeYmid.clinical_history_service.domain.attachment.Attachment;
import com.ClinicaDeYmid.clinical_history_service.domain.copy.DocumentSealer;
import com.ClinicaDeYmid.clinical_history_service.domain.copy.RecordCopies;
import com.ClinicaDeYmid.clinical_history_service.domain.copy.RecordCopy;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounters;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainLink;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainLinks;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainVerification;
import com.ClinicaDeYmid.clinical_history_service.domain.note.ClinicalNotes;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteVoid;
import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReferences;
import com.ClinicaDeYmid.clinical_history_service.domain.update.PatientChart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecordCopyService {

    private static final Logger log = LoggerFactory.getLogger(RecordCopyService.class);
    private static final int PAGE = 200;

    public record Requester(UUID uuid, String role) {
    }

    public record Generated(RecordCopy copy, byte[] document) {
    }

    public record Verification(RecordCopy copy, boolean documentMatches, boolean sealValid) {
    }

    private final PatientDirectory patients;
    private final PatientReferences references;
    private final Encounters encounters;
    private final ClinicalNotes notes;
    private final PatientChart chart;
    private final IntegrityQueries integrity;
    private final ChainLinks links;
    private final DocumentSealer sealer;
    private final RecordCopyRenderer renderer;
    private final RecordCopies copies;
    private final AccessAudit audit;
    private final TransactionOperations transactions;
    private final Clock clock;

    public RecordCopyService(PatientDirectory patients, PatientReferences references, Encounters encounters, ClinicalNotes notes,
                             PatientChart chart, IntegrityQueries integrity, ChainLinks links, DocumentSealer sealer,
                             RecordCopyRenderer renderer,
                             RecordCopies copies, AccessAudit audit, TransactionOperations transactions, Clock clock) {
        this.patients = patients;
        this.references = references;
        this.encounters = encounters;
        this.notes = notes;
        this.chart = chart;
        this.integrity = integrity;
        this.links = links;
        this.sealer = sealer;
        this.renderer = renderer;
        this.copies = copies;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Generated generate(UUID patientUuid, String reason, Instant from, Instant to, Requester requester, String sealAlgorithm,
                              String activeSealKeyId) {
        String justification = RecordCopy.requireReason(reason);
        RecordCopy.requirePeriod(from, to);
        PatientReference patient = switch (patients.find(patientUuid)) {
            case PatientLookup.Found found -> found.patient();
            case PatientLookup.NotFound notFound -> throw new ClinicalException.PatientNotFound();
            case PatientLookup.Unavailable unavailable -> throw new ClinicalException.PatientRegistryUnavailable();
        };
        UUID copyId = UUID.randomUUID();
        Instant generatedAt = Instant.now(clock);
        RecordCopyContent content = transactions.execute(status -> {
            List<UUID> subjects = patients.samePersonSubjects(patientUuid);
            List<PatientReference> linked = subjects.stream().filter(subject -> !subject.equals(patientUuid))
                    .map(references::find).flatMap(Optional::stream).toList();
            List<EncounterRecord> records = allEncounters(subjects).stream()
                    .filter(encounter -> (from == null || !encounter.openedAt().isBefore(from)) && (to == null || encounter.openedAt().isBefore(to)))
                    .sorted(Comparator.comparing(Encounter::openedAt))
                    .map(this::recordOf)
                    .toList();
            return new RecordCopyContent(copyId, patient, linked, generatedAt, requester.uuid(), requester.role(), justification, from, to,
                    records, chart.listItemsOf(subjects), chart.vitalSignsOf(subjects, null, from, to), integrity.verifyPatient(patientUuid),
                    subjects.stream().flatMap(subject -> links.chainOf(subject).stream())
                            .collect(Collectors.toMap(ChainLink::entryKey, Function.identity(), (first, second) -> first)),
                    sealAlgorithm, activeSealKeyId);
        });
        byte[] document = renderer.render(content);
        String sha256 = Attachment.sha256Of(document);
        DocumentSealer.DocumentSeal seal = sealer.sealDocument(sha256);
        int entries = content.chains().stream().mapToInt(chain -> (int) chain.entries()).sum();
        boolean verified = content.chains().stream().allMatch(ChainVerification::verified);
        boolean restricted = content.encounters().stream().flatMap(record -> record.notes().stream()).anyMatch(entry -> entry.note().isRestricted());
        RecordCopy copy = new RecordCopy(copyId, patientUuid, requester.uuid(), requester.role(), justification, from, to, entries, verified, sha256,
                seal.keyId(), seal.value(), generatedAt);
        transactions.executeWithoutResult(status -> {
            copies.add(copy);
            audit.record(new AccessEvent(patientUuid, new AccessEvent.Actor(requester.uuid(), requester.role()), AccessAction.EXPORT_RECORD, copyId,
                    AccessEvent.Outcome.GRANTED, AccessBasis.RECORDS_OFFICE, restricted, null, justification, generatedAt));
        });
        log.info("Record copy {} of patient {} generated by {} ({} bytes, chain verified: {})", copyId, patientUuid, requester.uuid(),
                document.length, verified);
        return new Generated(copy, document);
    }

    public RecordCopy find(UUID copyId) {
        return copies.find(copyId).orElseThrow(ClinicalException.RecordCopyNotFound::new);
    }

    public Verification verify(UUID copyId, byte[] document) {
        RecordCopy copy = find(copyId);
        boolean matches = Attachment.sha256Of(document).equals(copy.documentSha256());
        boolean sealValid = sealer.verifyDocument(copy.documentSha256(), new DocumentSealer.DocumentSeal(copy.keyId(), copy.seal()));
        return new Verification(copy, matches, sealValid);
    }

    private List<Encounter> allEncounters(List<UUID> subjects) {
        List<Encounter> all = new ArrayList<>();
        for (int page = 0; ; page++) {
            List<Encounter> batch = encounters.ofSubjects(subjects, page, PAGE);
            all.addAll(batch);
            if (batch.size() < PAGE) {
                return all;
            }
        }
    }

    private EncounterRecord recordOf(Encounter encounter) {
        Map<UUID, NoteVoid> voids = notes.voidsInEncounter(encounter.id()).stream().collect(Collectors.toMap(NoteVoid::noteId, Function.identity()));
        return new EncounterRecord(encounter, notes.ofEncounter(encounter.id()).stream()
                .map(note -> new NoteEntry(note, Optional.ofNullable(voids.get(note.id()))))
                .toList());
    }
}
