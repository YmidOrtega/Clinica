package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

import com.ClinicaDeYmid.clinical_history_service.application.integrity.IntegrityQueries.NoteSignature;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainLink;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainVerification;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.IntegrityProblem;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.web.ClinicalResponses.SignerView;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class IntegrityResponses {

    private IntegrityResponses() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ProblemView(String kind, Long sequence, String entryType, UUID entryId) {
        static ProblemView from(IntegrityProblem problem) {
            return new ProblemView(problem.kind().name(), problem.sequence(), problem.entryType().name(), problem.entryId());
        }
    }

    record HeadView(long sequence, String entryHash, Instant sealedAt) {
        static HeadView from(ChainLink link) {
            return link == null ? null : new HeadView(link.sequence(), link.entryHash(), link.sealedAt());
        }
    }

    record ChainView(UUID subjectUuid, boolean verified, long entries, HeadView head, List<ProblemView> problems) {
        static ChainView from(ChainVerification verification) {
            return new ChainView(verification.patientUuid(), verification.verified(), verification.entries(),
                    HeadView.from(verification.head()), verification.problems().stream().map(ProblemView::from).toList());
        }
    }

    record PatientIntegrityView(UUID patientUuid, boolean verified, Instant checkedAt, List<ChainView> chains) {
        static PatientIntegrityView from(UUID patientUuid, List<ChainVerification> chains, Instant checkedAt) {
            return new PatientIntegrityView(patientUuid, chains.stream().allMatch(ChainVerification::verified), checkedAt,
                    chains.stream().map(ChainView::from).toList());
        }
    }

    record SealView(String algorithm, String keyId, String value) {
    }

    record LinkView(UUID patientUuid, long sequence, int formatVersion, String payloadHash, String previousHash, String entryHash,
                    Instant sealedAt, SealView seal) {
        static LinkView from(ChainLink link, String algorithm) {
            return new LinkView(link.patientUuid(), link.sequence(), link.formatVersion(), link.payloadHash(), link.previousHash(),
                    link.entryHash(), link.sealedAt(), new SealView(algorithm, link.keyId(), link.seal()));
        }
    }

    record NoteSignatureView(UUID noteId, SignerView signer, Instant signedAt, boolean verified, List<String> problems, LinkView chain) {
        static NoteSignatureView from(NoteSignature signature, String algorithm) {
            return new NoteSignatureView(signature.note().id(), SignerView.from(signature.note()), signature.note().recordedAt(),
                    signature.verified(), signature.problems().stream().sorted().map(Enum::name).toList(),
                    signature.link().map(link -> LinkView.from(link, algorithm)).orElse(null));
        }
    }

    record SealKeyView(String keyId, String algorithm, String curve, String publicKeyPem, boolean active) {
    }
}
