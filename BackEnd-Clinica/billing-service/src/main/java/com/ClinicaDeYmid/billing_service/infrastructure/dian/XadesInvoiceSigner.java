package com.ClinicaDeYmid.billing_service.infrastructure.dian;

import com.ClinicaDeYmid.billing_service.application.dian.InvoiceSigner;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.security.auth.x500.X500Principal;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class XadesInvoiceSigner implements InvoiceSigner {

    public static final String POLICY_IDENTIFIER =
            "https://facturaelectronica.dian.gov.co/politicadefirma/v2/politicadefirmav2.pdf";
    public static final String POLICY_DESCRIPTION =
            "Política de firma para facturas electrónicas de la República de Colombia.";
    public static final String POLICY_SHA256 = "dMoMvtcG5aIzgYo0tIsSQeVJBDnUnfSOfBpxXrmor0Y=";

    static final String C14N = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315";
    static final String RSA_SHA256 = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";
    static final String SHA256 = "http://www.w3.org/2001/04/xmlenc#sha256";
    static final String ENVELOPED = "http://www.w3.org/2000/09/xmldsig#enveloped-signature";
    static final String SIGNED_PROPERTIES = "http://uri.etsi.org/01903#SignedProperties";

    private static final DateTimeFormatter SIGNING_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private final DianSigningKey key;
    private final ZoneId zone;

    public XadesInvoiceSigner(DianSigningKey key, ZoneId zone) {
        this.key = key;
        this.zone = zone;
    }

    @Override
    public String sign(String unsignedUbl, Instant signingTime) {
        List<X509Certificate> chain = key.certificateChain();
        X509Certificate certificate = chain.getFirst();
        try {
            certificate.checkValidity(Date.from(signingTime));
        } catch (CertificateExpiredException | CertificateNotYetValidException outside) {
            throw new BillingException.DianCertificateInvalid(
                    "El certificado de firma DIAN no está vigente en la fecha de firma");
        }
        try {
            Document document = XmlSupport.parse(unsignedUbl.getBytes(StandardCharsets.UTF_8));
            Element slot = signatureSlot(document);
            String documentDigest = XmlSupport.sha256(XmlSupport.inclusive(document));

            String id = "xmldsig-" + UUID.randomUUID();
            Element signature = ds(document, "Signature");
            signature.setAttribute("Id", id);
            Element signedInfo = XmlSupport.child(signature, StaxUblWriter.DS, "ds:SignedInfo");
            XmlSupport.child(signedInfo, StaxUblWriter.DS, "ds:CanonicalizationMethod").setAttribute("Algorithm", C14N);
            XmlSupport.child(signedInfo, StaxUblWriter.DS, "ds:SignatureMethod").setAttribute("Algorithm", RSA_SHA256);
            Element documentReference = reference(signedInfo, "");
            documentReference.setAttribute("Id", id + "-ref0");
            Element transforms = ds(document, "Transforms");
            XmlSupport.child(transforms, StaxUblWriter.DS, "ds:Transform").setAttribute("Algorithm", ENVELOPED);
            documentReference.insertBefore(transforms, documentReference.getFirstChild());
            digestValue(documentReference).setTextContent(documentDigest);
            Element keyInfoReference = reference(signedInfo, "#" + id + "-keyinfo");
            Element propertiesReference = reference(signedInfo, "#" + id + "-signedprops");
            propertiesReference.setAttribute("Type", SIGNED_PROPERTIES);

            Element signatureValue = XmlSupport.child(signature, StaxUblWriter.DS, "ds:SignatureValue");
            signatureValue.setAttribute("Id", id + "-sigvalue");
            Element keyInfo = XmlSupport.child(signature, StaxUblWriter.DS, "ds:KeyInfo");
            keyInfo.setAttribute("Id", id + "-keyinfo");
            XmlSupport.child(XmlSupport.child(keyInfo, StaxUblWriter.DS, "ds:X509Data"), StaxUblWriter.DS, "ds:X509Certificate")
                    .setTextContent(XmlSupport.base64(certificate.getEncoded()));
            Element qualifying = XmlSupport.child(XmlSupport.child(signature, StaxUblWriter.DS, "ds:Object"), StaxUblWriter.XADES,
                    "xades:QualifyingProperties");
            qualifying.setAttribute("Target", "#" + id);
            Element signedProperties = XmlSupport.child(qualifying, StaxUblWriter.XADES, "xades:SignedProperties");
            signedProperties.setAttribute("Id", id + "-signedprops");
            signatureProperties(signedProperties, chain, signingTime);

            slot.appendChild(signature);
            digestValue(keyInfoReference).setTextContent(XmlSupport.sha256(XmlSupport.inclusive(keyInfo)));
            digestValue(propertiesReference).setTextContent(XmlSupport.sha256(XmlSupport.inclusive(signedProperties)));
            signatureValue.setTextContent(XmlSupport.base64(key.signSha256WithRsa(XmlSupport.inclusive(signedInfo))));
            return XmlSupport.serialize(document);
        } catch (CertificateEncodingException broken) {
            throw new IllegalStateException("Cannot sign the UBL invoice", broken);
        }
    }

    private void signatureProperties(Element signedProperties, List<X509Certificate> chain, Instant signingTime)
            throws CertificateEncodingException {
        Element properties = XmlSupport.child(signedProperties, StaxUblWriter.XADES, "xades:SignedSignatureProperties");
        XmlSupport.child(properties, StaxUblWriter.XADES, "xades:SigningTime")
                .setTextContent(SIGNING_TIME.format(signingTime.atZone(zone)));
        Element signingCertificate = XmlSupport.child(properties, StaxUblWriter.XADES, "xades:SigningCertificate");
        for (X509Certificate certificate : chain) {
            Element cert = XmlSupport.child(signingCertificate, StaxUblWriter.XADES, "xades:Cert");
            Element certDigest = XmlSupport.child(cert, StaxUblWriter.XADES, "xades:CertDigest");
            XmlSupport.child(certDigest, StaxUblWriter.DS, "ds:DigestMethod").setAttribute("Algorithm", SHA256);
            XmlSupport.child(certDigest, StaxUblWriter.DS, "ds:DigestValue").setTextContent(XmlSupport.sha256(certificate.getEncoded()));
            Element issuerSerial = XmlSupport.child(cert, StaxUblWriter.XADES, "xades:IssuerSerial");
            XmlSupport.child(issuerSerial, StaxUblWriter.DS, "ds:X509IssuerName")
                    .setTextContent(certificate.getIssuerX500Principal().getName(X500Principal.RFC2253));
            XmlSupport.child(issuerSerial, StaxUblWriter.DS, "ds:X509SerialNumber")
                    .setTextContent(certificate.getSerialNumber().toString());
        }
        Element policyId = XmlSupport.child(XmlSupport.child(properties, StaxUblWriter.XADES, "xades:SignaturePolicyIdentifier"),
                StaxUblWriter.XADES, "xades:SignaturePolicyId");
        Element sigPolicyId = XmlSupport.child(policyId, StaxUblWriter.XADES, "xades:SigPolicyId");
        XmlSupport.child(sigPolicyId, StaxUblWriter.XADES, "xades:Identifier").setTextContent(POLICY_IDENTIFIER);
        XmlSupport.child(sigPolicyId, StaxUblWriter.XADES, "xades:Description").setTextContent(POLICY_DESCRIPTION);
        Element policyHash = XmlSupport.child(policyId, StaxUblWriter.XADES, "xades:SigPolicyHash");
        XmlSupport.child(policyHash, StaxUblWriter.DS, "ds:DigestMethod").setAttribute("Algorithm", SHA256);
        XmlSupport.child(policyHash, StaxUblWriter.DS, "ds:DigestValue").setTextContent(POLICY_SHA256);
        Element roles = XmlSupport.child(XmlSupport.child(properties, StaxUblWriter.XADES, "xades:SignerRole"), StaxUblWriter.XADES,
                "xades:ClaimedRoles");
        XmlSupport.child(roles, StaxUblWriter.XADES, "xades:ClaimedRole").setTextContent("supplier");
    }

    private static Element signatureSlot(Document document) {
        NodeList extensions = document.getDocumentElement()
                .getElementsByTagNameNS(StaxUblWriter.EXT, "UBLExtension");
        if (extensions.getLength() < 2) {
            throw new IllegalArgumentException("The UBL has no second UBLExtension for the signature");
        }
        NodeList contents = ((Element) extensions.item(1))
                .getElementsByTagNameNS(StaxUblWriter.EXT, "ExtensionContent");
        if (contents.getLength() != 1 || contents.item(0).hasChildNodes()) {
            throw new IllegalArgumentException("The signature slot of the UBL is not empty");
        }
        return (Element) contents.item(0);
    }

    private static Element reference(Element signedInfo, String uri) {
        Element reference = XmlSupport.child(signedInfo, StaxUblWriter.DS, "ds:Reference");
        reference.setAttribute("URI", uri);
        XmlSupport.child(reference, StaxUblWriter.DS, "ds:DigestMethod").setAttribute("Algorithm", SHA256);
        XmlSupport.child(reference, StaxUblWriter.DS, "ds:DigestValue");
        return reference;
    }

    private static Element digestValue(Element reference) {
        return (Element) reference.getElementsByTagNameNS(StaxUblWriter.DS, "DigestValue").item(0);
    }

    private static Element ds(Document document, String name) {
        return document.createElementNS(StaxUblWriter.DS, "ds:" + name);
    }
}
