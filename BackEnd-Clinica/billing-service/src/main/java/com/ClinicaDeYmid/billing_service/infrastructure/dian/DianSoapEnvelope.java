package com.ClinicaDeYmid.billing_service.infrastructure.dian;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.SequencedMap;
import java.util.UUID;

public class DianSoapEnvelope {

    static final String SOAP = "http://www.w3.org/2003/05/soap-envelope";
    static final String WCF = "http://wcf.dian.colombia";
    static final String WSA = "http://www.w3.org/2005/08/addressing";
    static final String WSSE = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    static final String WSU = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
    static final String EXC_C14N = "http://www.w3.org/2001/10/xml-exc-c14n#";
    static final String X509_V3 =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3";
    static final String BASE64_BINARY =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary";
    static final String SIGNED_INFO_PREFIXES = "wsa soap wcf";
    static final String TO_PREFIXES = "soap wcf";

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);
    private static final Duration TIME_TO_LIVE = Duration.ofMinutes(5);

    private final DianSigningKey key;
    private final Clock clock;

    public DianSoapEnvelope(DianSigningKey key, Clock clock) {
        this.key = key;
        this.clock = clock;
    }

    public String operation(String to, String operation, SequencedMap<String, String> parameters) {
        X509Certificate certificate = key.certificateChain().getFirst();
        Document document = XmlSupport.empty();
        Element envelope = document.createElementNS(SOAP, "soap:Envelope");
        document.appendChild(envelope);
        XmlSupport.declare(envelope, "soap", SOAP);
        XmlSupport.declare(envelope, "wcf", WCF);
        Element header = XmlSupport.child(envelope, SOAP, "soap:Header");
        XmlSupport.declare(header, "wsa", WSA);
        Element security = XmlSupport.child(header, WSSE, "wsse:Security");
        XmlSupport.declare(security, "wsse", WSSE);
        XmlSupport.declare(security, "wsu", WSU);
        String id = UUID.randomUUID().toString();

        Instant now = clock.instant();
        Element timestamp = XmlSupport.child(security, WSU, "wsu:Timestamp");
        timestamp.setAttributeNS(WSU, "wsu:Id", "TS-" + id);
        XmlSupport.child(timestamp, WSU, "wsu:Created", TIMESTAMP.format(now));
        XmlSupport.child(timestamp, WSU, "wsu:Expires", TIMESTAMP.format(now.plus(TIME_TO_LIVE)));
        Element token = XmlSupport.child(security, WSSE, "wsse:BinarySecurityToken", encoded(certificate));
        token.setAttribute("EncodingType", BASE64_BINARY);
        token.setAttribute("ValueType", X509_V3);
        token.setAttributeNS(WSU, "wsu:Id", "X509-" + id);

        Element signature = XmlSupport.child(security, StaxUblWriter.DS, "ds:Signature");
        XmlSupport.declare(signature, "ds", StaxUblWriter.DS);
        signature.setAttribute("Id", "SIG-" + id);
        Element signedInfo = XmlSupport.child(signature, StaxUblWriter.DS, "ds:SignedInfo");
        exclusive(XmlSupport.child(signedInfo, StaxUblWriter.DS, "ds:CanonicalizationMethod"), SIGNED_INFO_PREFIXES);
        XmlSupport.child(signedInfo, StaxUblWriter.DS, "ds:SignatureMethod")
                .setAttribute("Algorithm", XadesDocumentSigner.RSA_SHA256);
        Element reference = XmlSupport.child(signedInfo, StaxUblWriter.DS, "ds:Reference");
        reference.setAttribute("URI", "#ID-" + id);
        Element transforms = XmlSupport.child(reference, StaxUblWriter.DS, "ds:Transforms");
        exclusive(XmlSupport.child(transforms, StaxUblWriter.DS, "ds:Transform"), TO_PREFIXES);
        XmlSupport.child(reference, StaxUblWriter.DS, "ds:DigestMethod")
                .setAttribute("Algorithm", XadesDocumentSigner.SHA256);
        Element digest = XmlSupport.child(reference, StaxUblWriter.DS, "ds:DigestValue");
        Element signatureValue = XmlSupport.child(signature, StaxUblWriter.DS, "ds:SignatureValue");
        Element keyInfo = XmlSupport.child(signature, StaxUblWriter.DS, "ds:KeyInfo");
        keyInfo.setAttribute("Id", "KI-" + id);
        Element tokenReference = XmlSupport.child(keyInfo, WSSE, "wsse:SecurityTokenReference");
        tokenReference.setAttributeNS(WSU, "wsu:Id", "STR-" + id);
        Element tokenPointer = XmlSupport.child(tokenReference, WSSE, "wsse:Reference");
        tokenPointer.setAttribute("URI", "#X509-" + id);
        tokenPointer.setAttribute("ValueType", X509_V3);

        XmlSupport.child(header, WSA, "wsa:Action", action(operation));
        Element addressedTo = XmlSupport.child(header, WSA, "wsa:To", to);
        XmlSupport.declare(addressedTo, "wsu", WSU);
        addressedTo.setAttributeNS(WSU, "wsu:Id", "ID-" + id);

        Element body = XmlSupport.child(envelope, SOAP, "soap:Body");
        Element call = XmlSupport.child(body, WCF, "wcf:" + operation);
        parameters.forEach((name, value) -> XmlSupport.child(call, WCF, "wcf:" + name, value));

        digest.setTextContent(XmlSupport.sha256(XmlSupport.exclusive(addressedTo, TO_PREFIXES)));
        signatureValue.setTextContent(XmlSupport.base64(
                key.signSha256WithRsa(XmlSupport.exclusive(signedInfo, SIGNED_INFO_PREFIXES))));
        return XmlSupport.serialize(document);
    }

    public static String action(String operation) {
        return WCF + "/IWcfDianCustomerServices/" + operation;
    }

    private static void exclusive(Element method, String prefixes) {
        method.setAttribute("Algorithm", EXC_C14N);
        Element inclusive = XmlSupport.child(method, EXC_C14N, "ec:InclusiveNamespaces");
        XmlSupport.declare(inclusive, "ec", EXC_C14N);
        inclusive.setAttribute("PrefixList", prefixes);
    }

    private static String encoded(X509Certificate certificate) {
        try {
            return XmlSupport.base64(certificate.getEncoded());
        } catch (CertificateEncodingException broken) {
            throw new IllegalStateException("Cannot encode the DIAN certificate", broken);
        }
    }
}
