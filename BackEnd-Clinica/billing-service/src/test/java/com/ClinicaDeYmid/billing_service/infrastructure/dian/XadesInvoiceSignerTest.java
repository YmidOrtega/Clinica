package com.ClinicaDeYmid.billing_service.infrastructure.dian;

import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.support.LocalDianSigningKey;
import com.ClinicaDeYmid.billing_service.support.TestCertificates;
import com.ClinicaDeYmid.billing_service.support.XadesVerification;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XadesInvoiceSignerTest {

    private static final Instant SIGNED_AT = Instant.parse("2026-09-27T15:16:02.250Z");
    private static final LocalDianSigningKey KEY = LocalDianSigningKey.SHARED;
    private static final XadesInvoiceSigner SIGNER = new XadesInvoiceSigner(KEY, ZoneId.of("America/Bogota"));

    @Test
    void envelopsAnXadesEpesSignatureThatVerifiesOverTheWholeInvoice() throws Exception {
        String unsigned = StaxUblWriterTest.sampleUbl();

        String signed = SIGNER.sign(unsigned, SIGNED_AT);

        XadesVerification.Result result = XadesVerification.verify(signed, KEY.certificate().getPublicKey());
        assertThat(result.signatureValueValid()).isTrue();
        assertThat(result.references()).containsExactly(true, true, true);
        assertThat(result.valid()).isTrue();

        Document document = XadesVerification.parse(signed);
        XPath path = xpath();
        String signature = "/inv:Invoice/ext:UBLExtensions/ext:UBLExtension[2]/ext:ExtensionContent/ds:Signature";
        String properties = signature + "/ds:Object/xades:QualifyingProperties/xades:SignedProperties"
                + "/xades:SignedSignatureProperties";
        assertThat(path.evaluate("count(" + signature + ")", document)).isEqualTo("1");
        assertThat(path.evaluate(signature + "/ds:SignedInfo/ds:SignatureMethod/@Algorithm", document))
                .isEqualTo("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256");
        assertThat(path.evaluate(signature + "/ds:SignedInfo/ds:CanonicalizationMethod/@Algorithm", document))
                .isEqualTo("http://www.w3.org/TR/2001/REC-xml-c14n-20010315");
        assertThat(path.evaluate(signature + "/ds:SignedInfo/ds:Reference[1]/@URI", document)).isEmpty();
        assertThat(path.evaluate(signature + "/ds:SignedInfo/ds:Reference[3]/@Type", document))
                .isEqualTo("http://uri.etsi.org/01903#SignedProperties");
        assertThat(path.evaluate(signature + "/ds:Object/xades:QualifyingProperties/@Target", document))
                .isEqualTo("#" + path.evaluate(signature + "/@Id", document));
        assertThat(path.evaluate(properties + "/xades:SigningTime", document))
                .isEqualTo("2026-09-27T10:16:02.250-05:00");
        assertThat(path.evaluate(properties + "/xades:SigningCertificate/xades:Cert/xades:CertDigest/ds:DigestValue",
                document)).isEqualTo(Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(KEY.certificate().getEncoded())));
        assertThat(path.evaluate(properties + "/xades:SigningCertificate/xades:Cert/xades:IssuerSerial"
                + "/ds:X509SerialNumber", document)).isEqualTo(KEY.certificate().getSerialNumber().toString());
        assertThat(path.evaluate(properties + "/xades:SignaturePolicyIdentifier/xades:SignaturePolicyId"
                + "/xades:SigPolicyId/xades:Identifier", document))
                .isEqualTo("https://facturaelectronica.dian.gov.co/politicadefirma/v2/politicadefirmav2.pdf");
        assertThat(path.evaluate(properties + "/xades:SignaturePolicyIdentifier/xades:SignaturePolicyId"
                + "/xades:SigPolicyHash/ds:DigestValue", document)).isEqualTo("dMoMvtcG5aIzgYo0tIsSQeVJBDnUnfSOfBpxXrmor0Y=");
        assertThat(path.evaluate(properties + "/xades:SignerRole/xades:ClaimedRoles/xades:ClaimedRole", document))
                .isEqualTo("supplier");
        assertThat(path.evaluate("/inv:Invoice/cbc:UUID", document))
                .isEqualTo(path.evaluate("/inv:Invoice/cbc:UUID", XadesVerification.parse(unsigned)));
    }

    @Test
    void anyChangeToTheSignedInvoiceBreaksTheSignature() throws Exception {
        String signed = SIGNER.sign(StaxUblWriterTest.sampleUbl(), SIGNED_AT);
        String tampered = signed.replace("<cbc:PayableAmount currencyID=\"COP\">10000.00</cbc:PayableAmount>",
                "<cbc:PayableAmount currencyID=\"COP\">1000.00</cbc:PayableAmount>");

        assertThat(tampered).isNotEqualTo(signed);
        XadesVerification.Result result = XadesVerification.verify(tampered, KEY.certificate().getPublicKey());
        assertThat(result.valid()).isFalse();
        assertThat(result.references().getFirst()).isFalse();
    }

    @Test
    void refusesToSignWithACertificateOutsideItsValidity() {
        KeyPair keys = TestCertificates.rsaKeyPair();
        X509Certificate expired = TestCertificates.selfSigned(keys, "Vencido", Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:00:00Z"));
        XadesInvoiceSigner signer = new XadesInvoiceSigner(new DianSigningKey() {
            @Override
            public List<X509Certificate> certificateChain() {
                return List.of(expired);
            }

            @Override
            public byte[] signSha256WithRsa(byte[] data) {
                throw new AssertionError("An expired certificate must not reach the key");
            }
        }, ZoneId.of("America/Bogota"));

        assertThatThrownBy(() -> signer.sign(StaxUblWriterTest.sampleUbl(), SIGNED_AT))
                .isInstanceOf(BillingException.DianCertificateInvalid.class);
    }

    @Test
    void neverSignsADocumentTwice() {
        String signed = SIGNER.sign(StaxUblWriterTest.sampleUbl(), SIGNED_AT);

        assertThatThrownBy(() -> SIGNER.sign(signed, SIGNED_AT)).isInstanceOf(IllegalArgumentException.class);
    }

    private static XPath xpath() {
        XPath path = XPathFactory.newInstance().newXPath();
        Map<String, String> namespaces = Map.of("inv", StaxUblWriter.INVOICE, "cbc", StaxUblWriter.CBC,
                "ext", StaxUblWriter.EXT, "ds", StaxUblWriter.DS, "xades", StaxUblWriter.XADES);
        path.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return namespaces.getOrDefault(prefix, XMLConstants.NULL_NS_URI);
            }

            @Override
            public String getPrefix(String uri) {
                return null;
            }

            @Override
            public Iterator<String> getPrefixes(String uri) {
                return null;
            }
        });
        return path;
    }
}
