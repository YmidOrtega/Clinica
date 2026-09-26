package com.ClinicaDeYmid.billing_service.support;

import com.ClinicaDeYmid.billing_service.domain.GraphicRepresentation;
import com.ClinicaDeYmid.commons.documents.DocumentSealer;
import com.ClinicaDeYmid.commons.documents.LocalSealSigner;
import com.ClinicaDeYmid.commons.documents.SealKeyRing;
import com.ClinicaDeYmid.commons.documents.TransitDocumentSealer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;

@TestConfiguration(proxyBeanMethods = false)
public class LocalSeal {

    public static final String KEY_ID = "billing-seal-test-v1";

    @Bean
    @Primary
    DocumentSealer localRepresentationSealer() {
        return new TransitDocumentSealer(SealKeyRing.of(new LocalSealSigner(KEY_ID), Map.of()),
                GraphicRepresentation.PURPOSE);
    }
}
