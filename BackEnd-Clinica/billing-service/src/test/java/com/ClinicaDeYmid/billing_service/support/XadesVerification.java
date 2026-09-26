package com.ClinicaDeYmid.billing_service.support;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.List;

public final class XadesVerification {

    public static final String DS = "http://www.w3.org/2000/09/xmldsig#";
    public static final String XADES = "http://uri.etsi.org/01903/v1.3.2#";

    private XadesVerification() {
    }

    public static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    public static Result verify(String xml, PublicKey key) throws Exception {
        Document document = parse(xml);
        NodeList signatures = document.getElementsByTagNameNS(DS, "Signature");
        if (signatures.getLength() != 1) {
            throw new AssertionError("Expected one signature but found " + signatures.getLength());
        }
        registerIds(document.getElementsByTagNameNS(DS, "KeyInfo"));
        registerIds(document.getElementsByTagNameNS(XADES, "SignedProperties"));
        DOMValidateContext context = new DOMValidateContext(key, signatures.item(0));
        XMLSignature signature = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context);
        boolean valid = signature.validate(context);
        List<Boolean> references = ((List<?>) signature.getSignedInfo().getReferences()).stream()
                .map(reference -> {
                    try {
                        return ((Reference) reference).validate(context);
                    } catch (Exception broken) {
                        return false;
                    }
                }).toList();
        return new Result(valid, signature.getSignatureValue().validate(context), references);
    }

    private static void registerIds(NodeList elements) {
        for (int i = 0; i < elements.getLength(); i++) {
            ((Element) elements.item(i)).setIdAttributeNS(null, "Id", true);
        }
    }

    public record Result(boolean valid, boolean signatureValueValid, List<Boolean> references) {
    }
}
