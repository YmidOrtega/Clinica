package com.ClinicaDeYmid.api_gateway.infrastructure.routing;

import jakarta.servlet.http.HttpServletRequest;

public final class PublicApiPaths {

    public static final String RECEIPT_VERIFICATION = "/api/v1/admissions/receipts/verification";
    public static final String ADMISSIONS_SEAL_KEYS = "/api/v1/admissions/seal-keys";
    public static final String CLINICAL_SEAL_KEYS = "/api/v1/clinical/seal-keys";
    public static final String REPRESENTATION_VERIFICATION = "/api/v1/billing/graphic-representations/verification";
    public static final String BILLING_SEAL_KEYS = "/api/v1/billing/seal-keys";

    private PublicApiPaths() {
    }

    public static boolean reachedWithoutASession(HttpServletRequest request) {
        String path = request.getRequestURI();
        return RECEIPT_VERIFICATION.equals(path) || ADMISSIONS_SEAL_KEYS.equals(path)
                || CLINICAL_SEAL_KEYS.equals(path) || REPRESENTATION_VERIFICATION.equals(path)
                || BILLING_SEAL_KEYS.equals(path);
    }
}
