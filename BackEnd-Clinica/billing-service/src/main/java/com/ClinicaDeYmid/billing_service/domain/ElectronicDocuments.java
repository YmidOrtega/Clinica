package com.ClinicaDeYmid.billing_service.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ElectronicDocuments {

    ElectronicDocument save(ElectronicDocument document);

    Optional<ElectronicDocument> findByUuid(UUID uuid);

    Optional<ElectronicDocument> ofInvoice(UUID invoiceUuid);

    Optional<ElectronicDocument> ofCreditNote(UUID creditNoteUuid);

    List<UUID> awaitingSignature(int limit);

    List<UUID> awaitingDelivery(int limit);

    List<UUID> awaitingDianValidation(int limit);

    List<UUID> awaitingAttachment(int limit);
}
