package com.ClinicaDeYmid.billing_service.infrastructure.dian;

import com.ClinicaDeYmid.billing_service.application.dian.InvoiceSigner;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import org.apache.xml.security.Init;
import org.apache.xml.security.c14n.CanonicalizationException;
import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.c14n.InvalidCanonicalizerException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.security.auth.x500.X500Principal;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
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

    static {
        Init.init();
    }

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
            Document document = parse(unsignedUbl);
            Element slot = signatureSlot(document);
            String documentDigest = digest(canonical(document));

            String id = "xmldsig-" + UUID.randomUUID();
            Element signature = ds(document, "Signature");
            signature.setAttribute("Id", id);
            Element signedInfo = child(signature, StaxUblWriter.DS, "ds:SignedInfo");
            child(signedInfo, StaxUblWriter.DS, "ds:CanonicalizationMethod").setAttribute("Algorithm", C14N);
            child(signedInfo, StaxUblWriter.DS, "ds:SignatureMethod").setAttribute("Algorithm", RSA_SHA256);
            Element documentReference = reference(signedInfo, "");
            documentReference.setAttribute("Id", id + "-ref0");
            Element transforms = ds(document, "Transforms");
            child(transforms, StaxUblWriter.DS, "ds:Transform").setAttribute("Algorithm", ENVELOPED);
            documentReference.insertBefore(transforms, documentReference.getFirstChild());
            digestValue(documentReference).setTextContent(documentDigest);
            Element keyInfoReference = reference(signedInfo, "#" + id + "-keyinfo");
            Element propertiesReference = reference(signedInfo, "#" + id + "-signedprops");
            propertiesReference.setAttribute("Type", SIGNED_PROPERTIES);

            Element signatureValue = child(signature, StaxUblWriter.DS, "ds:SignatureValue");
            signatureValue.setAttribute("Id", id + "-sigvalue");
            Element keyInfo = child(signature, StaxUblWriter.DS, "ds:KeyInfo");
            keyInfo.setAttribute("Id", id + "-keyinfo");
            child(child(keyInfo, StaxUblWriter.DS, "ds:X509Data"), StaxUblWriter.DS, "ds:X509Certificate")
                    .setTextContent(base64(certificate.getEncoded()));
            Element qualifying = child(child(signature, StaxUblWriter.DS, "ds:Object"), StaxUblWriter.XADES,
                    "xades:QualifyingProperties");
            qualifying.setAttribute("Target", "#" + id);
            Element signedProperties = child(qualifying, StaxUblWriter.XADES, "xades:SignedProperties");
            signedProperties.setAttribute("Id", id + "-signedprops");
            signatureProperties(signedProperties, chain, signingTime);

            slot.appendChild(signature);
            digestValue(keyInfoReference).setTextContent(digest(canonical(keyInfo)));
            digestValue(propertiesReference).setTextContent(digest(canonical(signedProperties)));
            signatureValue.setTextContent(base64(key.signSha256WithRsa(canonical(signedInfo))));
            return serialize(document);
        } catch (ParserConfigurationException | SAXException | IOException | TransformerException
                 | CanonicalizationException | InvalidCanonicalizerException | CertificateEncodingException broken) {
            throw new IllegalStateException("Cannot sign the UBL invoice", broken);
        }
    }

    private void signatureProperties(Element signedProperties, List<X509Certificate> chain, Instant signingTime)
            throws CertificateEncodingException {
        Element properties = child(signedProperties, StaxUblWriter.XADES, "xades:SignedSignatureProperties");
        child(properties, StaxUblWriter.XADES, "xades:SigningTime")
                .setTextContent(SIGNING_TIME.format(signingTime.atZone(zone)));
        Element signingCertificate = child(properties, StaxUblWriter.XADES, "xades:SigningCertificate");
        for (X509Certificate certificate : chain) {
            Element cert = child(signingCertificate, StaxUblWriter.XADES, "xades:Cert");
            Element certDigest = child(cert, StaxUblWriter.XADES, "xades:CertDigest");
            child(certDigest, StaxUblWriter.DS, "ds:DigestMethod").setAttribute("Algorithm", SHA256);
            child(certDigest, StaxUblWriter.DS, "ds:DigestValue").setTextContent(digest(certificate.getEncoded()));
            Element issuerSerial = child(cert, StaxUblWriter.XADES, "xades:IssuerSerial");
            child(issuerSerial, StaxUblWriter.DS, "ds:X509IssuerName")
                    .setTextContent(certificate.getIssuerX500Principal().getName(X500Principal.RFC2253));
            child(issuerSerial, StaxUblWriter.DS, "ds:X509SerialNumber")
                    .setTextContent(certificate.getSerialNumber().toString());
        }
        Element policyId = child(child(properties, StaxUblWriter.XADES, "xades:SignaturePolicyIdentifier"),
                StaxUblWriter.XADES, "xades:SignaturePolicyId");
        Element sigPolicyId = child(policyId, StaxUblWriter.XADES, "xades:SigPolicyId");
        child(sigPolicyId, StaxUblWriter.XADES, "xades:Identifier").setTextContent(POLICY_IDENTIFIER);
        child(sigPolicyId, StaxUblWriter.XADES, "xades:Description").setTextContent(POLICY_DESCRIPTION);
        Element policyHash = child(policyId, StaxUblWriter.XADES, "xades:SigPolicyHash");
        child(policyHash, StaxUblWriter.DS, "ds:DigestMethod").setAttribute("Algorithm", SHA256);
        child(policyHash, StaxUblWriter.DS, "ds:DigestValue").setTextContent(POLICY_SHA256);
        Element roles = child(child(properties, StaxUblWriter.XADES, "xades:SignerRole"), StaxUblWriter.XADES,
                "xades:ClaimedRoles");
        child(roles, StaxUblWriter.XADES, "xades:ClaimedRole").setTextContent("supplier");
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
        Element reference = child(signedInfo, StaxUblWriter.DS, "ds:Reference");
        reference.setAttribute("URI", uri);
        child(reference, StaxUblWriter.DS, "ds:DigestMethod").setAttribute("Algorithm", SHA256);
        child(reference, StaxUblWriter.DS, "ds:DigestValue");
        return reference;
    }

    private static Element digestValue(Element reference) {
        return (Element) reference.getElementsByTagNameNS(StaxUblWriter.DS, "DigestValue").item(0);
    }

    private static Element ds(Document document, String name) {
        return document.createElementNS(StaxUblWriter.DS, "ds:" + name);
    }

    private static Element child(Element parent, String namespace, String qualifiedName) {
        Element element = parent.getOwnerDocument().createElementNS(namespace, qualifiedName);
        parent.appendChild(element);
        return element;
    }

    private static byte[] canonical(Node node) throws InvalidCanonicalizerException, CanonicalizationException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Canonicalizer.getInstance(Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS).canonicalizeSubtree(node, out);
        return out.toByteArray();
    }

    private static String digest(byte[] data) {
        try {
            return base64(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String base64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    private static Document parse(String xml) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String serialize(Document document) throws TransformerException {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        document.setXmlStandalone(true);
        StringWriter out = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(out));
        return out.toString();
    }
}
