package com.ClinicaDeYmid.clinical_history_service.domain.terminology;

import java.util.List;
import java.util.Optional;

public interface ConceptCatalog {

    Optional<CodedConcept> resolve(String code);

    List<Concept> search(String query, int limit);
}
