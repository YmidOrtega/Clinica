package com.ClinicaDeYmid.clinical_history_service.domain.integrity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public record ChainVerification(UUID patientUuid, long entries, ChainLink head, List<IntegrityProblem> problems) {

    public ChainVerification {
        problems = List.copyOf(problems);
    }

    public boolean verified() {
        return problems.isEmpty();
    }

    public static ChainVerification of(UUID patientUuid, Collection<ChainLink> links, Collection<LedgerEntry> recorded,
                                       ClinicalSignature signature) {
        Map<LedgerEntry.Key, LedgerEntry> pending = recorded.stream()
                .collect(Collectors.toMap(LedgerEntry::key, Function.identity(), (first, second) -> first, LinkedHashMap::new));
        List<IntegrityProblem> problems = new ArrayList<>();
        ChainLink previous = null;
        for (ChainLink link : links.stream().sorted(Comparator.comparingLong(ChainLink::sequence)).toList()) {
            if (link.sequence() != ChainLink.nextSequenceAfter(previous)) {
                problems.add(IntegrityProblem.at(IntegrityProblem.Kind.SEQUENCE_GAP, link));
            }
            if (!link.previousHash().equals(ChainLink.hashAfter(previous))) {
                problems.add(IntegrityProblem.at(IntegrityProblem.Kind.BROKEN_CHAIN, link));
            }
            LedgerEntry entry = pending.remove(link.entryKey());
            if (entry == null || !entry.patientUuid().equals(link.patientUuid())) {
                problems.add(IntegrityProblem.at(IntegrityProblem.Kind.MISSING_ENTRY, link));
            } else if (entry instanceof LedgerEntry.Unreadable) {
                problems.add(IntegrityProblem.at(IntegrityProblem.Kind.UNREADABLE_ENTRY, link));
            } else {
                signature.check(entry, link).stream().sorted()
                        .forEach(kind -> problems.add(IntegrityProblem.at(kind, link)));
            }
            previous = link;
        }
        pending.values().forEach(entry -> problems.add(IntegrityProblem.unsealed(entry)));
        return new ChainVerification(patientUuid, links.size(), previous, problems);
    }
}
