package com.ClinicaDeYmid.clinical_history_service.domain.integrity;

import java.util.UUID;

public record IntegrityProblem(Kind kind, Long sequence, EntryType entryType, UUID entryId) {

    public enum Kind {
        SEQUENCE_GAP,
        BROKEN_CHAIN,
        PAYLOAD_MISMATCH,
        ENTRY_HASH_MISMATCH,
        INVALID_SEAL,
        UNKNOWN_KEY,
        MISSING_ENTRY,
        UNSEALED_ENTRY
    }

    static IntegrityProblem at(Kind kind, ChainLink link) {
        return new IntegrityProblem(kind, link.sequence(), link.entryType(), link.entryId());
    }

    static IntegrityProblem unsealed(LedgerEntry entry) {
        return new IntegrityProblem(Kind.UNSEALED_ENTRY, null, entry.type(), entry.entryId());
    }
}
