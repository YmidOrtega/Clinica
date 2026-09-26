package com.ClinicaDeYmid.billing_service.domain;

import java.util.Optional;
import java.util.UUID;

public interface GraphicRepresentations {

    void add(GraphicRepresentation representation);

    Optional<GraphicRepresentation> find(UUID id);

    Optional<GraphicRepresentation> findByNumberAndFingerprint(String documentNumber, String sha256);
}
