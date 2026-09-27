package com.ClinicaDeYmid.billing_service.domain;

import java.util.List;
import java.util.Optional;

public interface ObjectionCatalog {

    Optional<ObjectionCode> find(String code);

    List<ObjectionCode> list(ObjectionCode.Kind kind);
}
