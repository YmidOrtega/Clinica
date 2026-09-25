package com.ClinicaDeYmid.billing_service.domain;

import java.util.Optional;

public interface Issuers {

    Issuer save(Issuer issuer);

    Optional<Issuer> find();
}
