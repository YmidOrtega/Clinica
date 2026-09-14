package com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity;

import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainLink;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ClinicalSignature;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.IntegrityProblem;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.LedgerEntry;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class EcdsaClinicalSignature implements ClinicalSignature {

    private final SealKeyRing keys;

    EcdsaClinicalSignature(SealKeyRing keys) {
        this.keys = keys;
    }

    @Override
    public ChainLink seal(LedgerEntry entry, ChainLink previous, Instant sealedAt) {
        long sequence = ChainLink.nextSequenceAfter(previous);
        String previousHash = ChainLink.hashAfter(previous);
        String payloadHash = CanonicalPayloads.sha256(CanonicalPayloads.payload(entry));
        String entryHash = CanonicalPayloads.sha256(CanonicalPayloads.entry(entry.patientUuid(), sequence, entry.type(), entry.entryId(),
                CanonicalPayloads.FORMAT_VERSION, payloadHash, previousHash, keys.activeKeyId(), sealedAt));
        String seal = Base64.getEncoder().encodeToString(keys.sign(entryHash.getBytes(StandardCharsets.US_ASCII)));
        return new ChainLink(entry.patientUuid(), sequence, entry.type(), entry.entryId(), CanonicalPayloads.FORMAT_VERSION, payloadHash,
                previousHash, entryHash, keys.activeKeyId(), seal, sealedAt);
    }

    @Override
    public Set<IntegrityProblem.Kind> check(LedgerEntry entry, ChainLink link) {
        Set<IntegrityProblem.Kind> problems = EnumSet.noneOf(IntegrityProblem.Kind.class);
        if (!CanonicalPayloads.sha256(CanonicalPayloads.payload(entry)).equals(link.payloadHash())) {
            problems.add(IntegrityProblem.Kind.PAYLOAD_MISMATCH);
        }
        if (!CanonicalPayloads.sha256(CanonicalPayloads.entry(link)).equals(link.entryHash())) {
            problems.add(IntegrityProblem.Kind.ENTRY_HASH_MISMATCH);
        }
        if (!keys.knows(link.keyId())) {
            problems.add(IntegrityProblem.Kind.UNKNOWN_KEY);
        } else if (!decode(link.seal())
                .flatMap(seal -> keys.verify(link.keyId(), link.entryHash().getBytes(StandardCharsets.US_ASCII), seal))
                .orElse(false)) {
            problems.add(IntegrityProblem.Kind.INVALID_SEAL);
        }
        return problems;
    }

    public String activeKeyId() {
        return keys.activeKeyId();
    }

    public String algorithm() {
        return SealKeyRing.ALGORITHM;
    }

    public Map<String, String> publicKeys() {
        return keys.publicKeysPem();
    }

    private static Optional<byte[]> decode(String seal) {
        try {
            return Optional.of(Base64.getDecoder().decode(seal));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
