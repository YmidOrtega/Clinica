package com.ClinicaDeYmid.clinical_history_service.domain.integrity;

import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.openEncounter;
import static org.assertj.core.api.Assertions.assertThat;

class ChainVerificationTest {

    private static final Instant AT = Instant.parse("2026-09-10T14:00:00Z");

    private final ClinicalSignature trustingSignature = new ClinicalSignature() {
        @Override
        public ChainLink seal(LedgerEntry entry, ChainLink previous, Instant sealedAt) {
            String hash = String.format("%064x", ChainLink.nextSequenceAfter(previous));
            return new ChainLink(entry.patientUuid(), ChainLink.nextSequenceAfter(previous), entry.type(), entry.entryId(), 1,
                    hash, ChainLink.hashAfter(previous), hash, "k1", "seal", sealedAt);
        }

        @Override
        public Set<IntegrityProblem.Kind> check(LedgerEntry entry, ChainLink link) {
            return Set.of();
        }
    };

    @Test
    void anIntactChainHasNoProblems() {
        Chain chain = chainOf(3);

        ChainVerification verification = ChainVerification.of(chain.patient, chain.links, chain.entries, trustingSignature);

        assertThat(verification.verified()).isTrue();
        assertThat(verification.entries()).isEqualTo(3);
        assertThat(verification.head()).isEqualTo(chain.links.get(2));
    }

    @Test
    void anEmptyRecordIsVerified() {
        assertThat(ChainVerification.of(UUID.randomUUID(), List.of(), List.of(), trustingSignature).verified()).isTrue();
    }

    @Test
    void detectsRemovedLinksAsGapsBrokenChainAndUnsealedEntries() {
        Chain chain = chainOf(3);
        ChainLink removed = chain.links.remove(1);

        ChainVerification verification = ChainVerification.of(chain.patient, chain.links, chain.entries, trustingSignature);

        assertThat(verification.problems()).containsExactly(
                new IntegrityProblem(IntegrityProblem.Kind.SEQUENCE_GAP, 3L, EntryType.ENCOUNTER_OPENED, chain.links.get(1).entryId()),
                new IntegrityProblem(IntegrityProblem.Kind.BROKEN_CHAIN, 3L, EntryType.ENCOUNTER_OPENED, chain.links.get(1).entryId()),
                new IntegrityProblem(IntegrityProblem.Kind.UNSEALED_ENTRY, null, EntryType.ENCOUNTER_OPENED, removed.entryId()));
    }

    @Test
    void detectsDeletedEntriesAndEntriesMovedToAnotherPatient() {
        Chain chain = chainOf(2);
        chain.entries.remove(0);
        Encounter moved = ((LedgerEntry.EncounterOpened) chain.entries.remove(0)).encounter();
        chain.entries.add(new LedgerEntry.EncounterOpened(new Encounter(moved.id(), UUID.randomUUID(), moved.type(), null,
                moved.openedAt(), moved.openedBy(), moved.status())));

        ChainVerification verification = ChainVerification.of(chain.patient, chain.links, chain.entries, trustingSignature);

        assertThat(verification.problems()).extracting(IntegrityProblem::kind).containsExactly(
                IntegrityProblem.Kind.MISSING_ENTRY, IntegrityProblem.Kind.MISSING_ENTRY);
    }

    @Test
    void reportsWhatTheSignatureFindsForEachLink() {
        Chain chain = chainOf(1);
        ClinicalSignature strict = new ClinicalSignature() {
            @Override
            public ChainLink seal(LedgerEntry entry, ChainLink previous, Instant sealedAt) {
                return trustingSignature.seal(entry, previous, sealedAt);
            }

            @Override
            public Set<IntegrityProblem.Kind> check(LedgerEntry entry, ChainLink link) {
                return Set.of(IntegrityProblem.Kind.INVALID_SEAL, IntegrityProblem.Kind.PAYLOAD_MISMATCH);
            }
        };

        assertThat(ChainVerification.of(chain.patient, chain.links, chain.entries, strict).problems())
                .extracting(IntegrityProblem::kind)
                .containsExactly(IntegrityProblem.Kind.PAYLOAD_MISMATCH, IntegrityProblem.Kind.INVALID_SEAL);
    }

    private Chain chainOf(int size) {
        UUID patient = UUID.randomUUID();
        List<ChainLink> links = new ArrayList<>();
        List<LedgerEntry> entries = new ArrayList<>();
        ChainLink previous = null;
        for (int i = 0; i < size; i++) {
            Encounter base = openEncounter(EncounterType.OUTPATIENT);
            LedgerEntry entry = new LedgerEntry.EncounterOpened(new Encounter(base.id(), patient, base.type(), null, base.openedAt(),
                    base.openedBy(), base.status()));
            previous = trustingSignature.seal(entry, previous, AT);
            links.add(previous);
            entries.add(entry);
        }
        return new Chain(patient, links, entries);
    }

    private record Chain(UUID patient, List<ChainLink> links, List<LedgerEntry> entries) {
    }
}
