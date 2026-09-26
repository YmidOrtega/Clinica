package com.ClinicaDeYmid.billing_service.infrastructure.dian;

import com.ClinicaDeYmid.billing_service.domain.BillingException;

import java.security.cert.X509Certificate;
import java.util.List;

public class UnavailableDianSigningKey implements DianSigningKey {

    @Override
    public List<X509Certificate> certificateChain() {
        throw new BillingException.DianSignatureUnavailable();
    }

    @Override
    public byte[] signSha256WithRsa(byte[] data) {
        throw new BillingException.DianSignatureUnavailable();
    }
}
