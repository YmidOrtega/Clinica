package com.ClinicaDeYmid.commons.documents;

public record DocumentVerification(boolean documentMatches, boolean sealValid) {

    public boolean authentic() {
        return documentMatches && sealValid;
    }
}
