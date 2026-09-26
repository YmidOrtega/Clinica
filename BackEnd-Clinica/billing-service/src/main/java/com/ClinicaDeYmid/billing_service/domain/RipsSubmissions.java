package com.ClinicaDeYmid.billing_service.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RipsSubmissions {

    RipsSubmission save(RipsSubmission submission);

    Optional<RipsSubmission> findByUuid(UUID uuid);

    List<RipsSubmission> ofInvoice(UUID invoiceUuid);

    List<UUID> pending(int limit);

    List<RipsSubmission> validatedOf(java.util.Collection<UUID> invoiceUuids);
}
