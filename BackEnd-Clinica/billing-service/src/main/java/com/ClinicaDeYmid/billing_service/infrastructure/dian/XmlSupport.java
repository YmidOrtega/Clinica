package com.ClinicaDeYmid.billing_service.infrastructure.dian;

import org.apache.xml.security.Init;
import org.apache.xml.security.c14n.CanonicalizationException;
import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.c14n.InvalidCanonicalizerException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

final class XmlSupport {

    static final String XMLNS = "http://www.w3.org/2000/xmlns/";

    static {
        Init.init();
    }

    private XmlSupport() {
    }

    static Document parse(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (ParserConfigurationException impossible) {
            throw new IllegalStateException(impossible);
        } catch (SAXException malformed) {
            throw new IllegalArgumentException("Malformed XML", malformed);
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
    }

    static Document empty() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static String serialize(Document document) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            document.setXmlStandalone(true);
            StringWriter out = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(out));
            return out.toString();
        } catch (TransformerException broken) {
            throw new IllegalStateException("Cannot serialize the XML", broken);
        }
    }

    static byte[] inclusive(Node node) {
        return canonical(Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS, node, null);
    }

    static byte[] exclusive(Node node, String inclusivePrefixes) {
        return canonical(Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS, node, inclusivePrefixes);
    }

    private static byte[] canonical(String algorithm, Node node, String inclusivePrefixes) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Canonicalizer canonicalizer = Canonicalizer.getInstance(algorithm);
            if (inclusivePrefixes == null) {
                canonicalizer.canonicalizeSubtree(node, out);
            } else {
                canonicalizer.canonicalizeSubtree(node, inclusivePrefixes, out);
            }
            return out.toByteArray();
        } catch (InvalidCanonicalizerException | CanonicalizationException broken) {
            throw new IllegalStateException("Cannot canonicalize the XML", broken);
        }
    }

    static String sha256(byte[] data) {
        try {
            return base64(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static String base64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    static Element child(Element parent, String namespace, String qualifiedName) {
        Element element = parent.getOwnerDocument().createElementNS(namespace, qualifiedName);
        parent.appendChild(element);
        return element;
    }

    static Element child(Element parent, String namespace, String qualifiedName, String text) {
        Element element = child(parent, namespace, qualifiedName);
        element.setTextContent(text);
        return element;
    }

    static void declare(Element element, String prefix, String namespace) {
        element.setAttributeNS(XMLNS, "xmlns:" + prefix, namespace);
    }
}
