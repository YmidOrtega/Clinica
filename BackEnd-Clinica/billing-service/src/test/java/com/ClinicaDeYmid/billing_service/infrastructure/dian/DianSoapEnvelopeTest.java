package com.ClinicaDeYmid.billing_service.infrastructure.dian;

import com.ClinicaDeYmid.billing_service.support.LocalDianSigningKey;
import com.ClinicaDeYmid.billing_service.support.XadesVerification;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.SequencedMap;

import static org.assertj.core.api.Assertions.assertThat;

class DianSoapEnvelopeTest {

    private static final String TO = "https://vpfe-hab.dian.gov.co/WcfDianCustomerServices.svc";
    private static final LocalDianSigningKey KEY = LocalDianSigningKey.SHARED;
    private static final DianSoapEnvelope ENVELOPE = new DianSoapEnvelope(KEY,
            Clock.fixed(Instant.parse("2026-09-26T15:00:00Z"), ZoneOffset.UTC));

    @Test
    void signsTheAddressWithWsSecurityUsingTheDianCertificate() throws Exception {
        String soap = ENVELOPE.operation(TO, "GetStatusZip", parameters("trackId", "zip-key"));

        Document document = XadesVerification.parse(soap);
        assertThat(validates(document)).isTrue();
        assertThat(single(document, DianSoapEnvelope.WSA, "Action").getTextContent())
                .isEqualTo("http://wcf.dian.colombia/IWcfDianCustomerServices/GetStatusZip");
        assertThat(single(document, DianSoapEnvelope.WSA, "To").getTextContent()).isEqualTo(TO);
        assertThat(single(document, DianSoapEnvelope.WSU, "Created").getTextContent())
                .isEqualTo("2026-09-26T15:00:00.000Z");
        assertThat(single(document, DianSoapEnvelope.WSU, "Expires").getTextContent())
                .isEqualTo("2026-09-26T15:05:00.000Z");
        assertThat(single(document, DianSoapEnvelope.WSSE, "BinarySecurityToken").getTextContent())
                .isEqualTo(Base64.getEncoder().encodeToString(KEY.certificate().getEncoded()));
        assertThat(single(document, DianSoapEnvelope.WSSE, "Reference").getAttribute("URI"))
                .isEqualTo("#" + single(document, DianSoapEnvelope.WSSE, "BinarySecurityToken")
                        .getAttributeNS(DianSoapEnvelope.WSU, "Id"));
        assertThat(single(document, DianSoapEnvelope.WCF, "trackId").getTextContent()).isEqualTo("zip-key");
    }

    @Test
    void anotherAddressBreaksTheSignature() throws Exception {
        String soap = ENVELOPE.operation(TO, "GetStatus", parameters("trackId", "cufe"));

        Document document = XadesVerification.parse(soap.replace(TO, "https://attacker.example/svc"));

        assertThat(validates(document)).isFalse();
    }

    @Test
    void keepsTheParametersInTheOrderOfTheContract() throws Exception {
        SequencedMap<String, String> parameters = parameters("fileName", "z1.zip");
        parameters.put("contentFile", "UEsDBA==");
        parameters.put("testSetId", "set");

        Document document = XadesVerification.parse(ENVELOPE.operation(TO, "SendTestSetAsync", parameters));

        Element call = single(document, DianSoapEnvelope.WCF, "SendTestSetAsync");
        assertThat(call.getChildNodes().item(0).getLocalName()).isEqualTo("fileName");
        assertThat(call.getChildNodes().item(1).getLocalName()).isEqualTo("contentFile");
        assertThat(call.getChildNodes().item(2).getLocalName()).isEqualTo("testSetId");
    }

    private static boolean validates(Document document) throws Exception {
        single(document, DianSoapEnvelope.WSA, "To").setIdAttributeNS(DianSoapEnvelope.WSU, "Id", true);
        DOMValidateContext context = new DOMValidateContext(KEY.certificate().getPublicKey(),
                single(document, XadesVerification.DS, "Signature"));
        XMLSignature signature = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context);
        return signature.validate(context);
    }

    private static SequencedMap<String, String> parameters(String name, String value) {
        SequencedMap<String, String> parameters = new LinkedHashMap<>();
        parameters.put(name, value);
        return parameters;
    }

    private static Element single(Document document, String namespace, String localName) {
        NodeList nodes = document.getElementsByTagNameNS(namespace, localName);
        assertThat(nodes.getLength()).isEqualTo(1);
        return (Element) nodes.item(0);
    }
}
