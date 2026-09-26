package com.ClinicaDeYmid.billing_service.infrastructure.dian;

import java.security.cert.X509Certificate;
import java.util.List;

public interface DianSigningKey {

    List<X509Certificate> certificateChain();

    byte[] signSha256WithRsa(byte[] data);
}
