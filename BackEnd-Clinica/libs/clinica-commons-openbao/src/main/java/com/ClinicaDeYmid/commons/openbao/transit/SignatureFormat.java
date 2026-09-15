package com.ClinicaDeYmid.commons.openbao.transit;

import java.util.Base64;

public enum SignatureFormat {

    DER("asn1", Base64.getDecoder()),
    JWS("jws", Base64.getUrlDecoder());

    private final String marshalingAlgorithm;
    private final Base64.Decoder decoder;

    SignatureFormat(String marshalingAlgorithm, Base64.Decoder decoder) {
        this.marshalingAlgorithm = marshalingAlgorithm;
        this.decoder = decoder;
    }

    String marshalingAlgorithm() {
        return marshalingAlgorithm;
    }

    byte[] decode(String signature) {
        return decoder.decode(signature);
    }
}
