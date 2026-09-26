package com.ClinicaDeYmid.billing_service.infrastructure.dian;

import com.ClinicaDeYmid.billing_service.application.dian.DianAnswer;
import com.ClinicaDeYmid.billing_service.application.dian.DianGateway;
import com.ClinicaDeYmid.billing_service.application.dian.DianReceipt;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.DianEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SequencedMap;

public class SoapDianGateway implements DianGateway {

    private static final Logger log = LoggerFactory.getLogger(SoapDianGateway.class);

    private final RestClient http;
    private final DianSoapEnvelope envelope;
    private final Map<DianEnvironment, String> urls;

    public SoapDianGateway(RestClient http, DianSoapEnvelope envelope, Map<DianEnvironment, String> urls) {
        this.http = http;
        this.envelope = envelope;
        this.urls = Map.copyOf(urls);
    }

    @Override
    public DianReceipt sendTestSet(DianEnvironment environment, String zipName, byte[] zip, String testSetId) {
        SequencedMap<String, String> parameters = new LinkedHashMap<>();
        parameters.put("fileName", zipName);
        parameters.put("contentFile", Base64.getEncoder().encodeToString(zip));
        parameters.put("testSetId", testSetId);
        Element result = call(environment, "SendTestSetAsync", parameters);
        List<String> errors = new ArrayList<>();
        for (Element list : descendants(result, "ErrorMessageList")) {
            for (Element message : descendants(list, "ProcessedMessage")) {
                errors.add(message.getTextContent().strip());
            }
        }
        return new DianReceipt(text(result, "ZipKey"), errors);
    }

    @Override
    public DianAnswer sendBill(DianEnvironment environment, String zipName, byte[] zip) {
        SequencedMap<String, String> parameters = new LinkedHashMap<>();
        parameters.put("fileName", zipName);
        parameters.put("contentFile", Base64.getEncoder().encodeToString(zip));
        return answer(call(environment, "SendBillSync", parameters));
    }

    @Override
    public DianAnswer statusOfZip(DianEnvironment environment, String trackId) {
        SequencedMap<String, String> parameters = new LinkedHashMap<>();
        parameters.put("trackId", trackId);
        List<Element> responses = descendants(call(environment, "GetStatusZip", parameters), "DianResponse");
        if (responses.isEmpty()) {
            return new DianAnswer(DianAnswer.Verdict.PROCESSING, null, null, List.of(), null);
        }
        return answer(responses.getFirst());
    }

    @Override
    public DianAnswer statusOfDocument(DianEnvironment environment, String cufe) {
        SequencedMap<String, String> parameters = new LinkedHashMap<>();
        parameters.put("trackId", cufe);
        return answer(call(environment, "GetStatus", parameters));
    }

    static DianAnswer answer(Element response) {
        boolean valid = "true".equalsIgnoreCase(text(response, "IsValid"));
        String code = text(response, "StatusCode");
        String description = text(response, "StatusDescription");
        List<String> errors = new ArrayList<>();
        for (Element list : descendants(response, "ErrorMessage")) {
            for (Element message : children(list)) {
                if (!message.getTextContent().isBlank()) {
                    errors.add(message.getTextContent().strip());
                }
            }
        }
        String encoded = text(response, "XmlBase64Bytes");
        String applicationResponse = encoded == null ? null
                : new String(Base64.getMimeDecoder().decode(encoded), StandardCharsets.UTF_8);
        DianAnswer.Verdict verdict;
        if (valid) {
            verdict = DianAnswer.Verdict.ACCEPTED;
        } else if (errors.isEmpty() && (code == null || (description != null
                && description.toLowerCase(Locale.ROOT).contains("en proceso")))) {
            verdict = DianAnswer.Verdict.PROCESSING;
        } else {
            verdict = DianAnswer.Verdict.REJECTED;
        }
        return new DianAnswer(verdict, code, description, errors, valid ? applicationResponse : null);
    }

    private Element call(DianEnvironment environment, String operation, SequencedMap<String, String> parameters) {
        String url = urls.get(environment);
        String request = envelope.operation(url, operation, parameters);
        byte[] response;
        try {
            response = http.post().uri(url)
                    .contentType(MediaType.parseMediaType("application/soap+xml;charset=UTF-8;action=\""
                            + DianSoapEnvelope.action(operation) + "\""))
                    .body(request.getBytes(StandardCharsets.UTF_8))
                    .exchange((sent, received) -> {
                        byte[] body = received.getBody().readAllBytes();
                        HttpStatusCode status = received.getStatusCode();
                        if (!status.is2xxSuccessful()) {
                            log.warn("The DIAN answered {} to {}: {}", status.value(), operation, fault(body));
                            throw new BillingException.DianUnavailable();
                        }
                        return body;
                    });
        } catch (RestClientException unreachable) {
            log.warn("The DIAN could not be reached for {}: {}", operation, unreachable.getMessage());
            throw new BillingException.DianUnavailable();
        }
        Document document;
        try {
            document = XmlSupport.parse(response);
        } catch (IllegalArgumentException malformed) {
            log.warn("The DIAN answered {} with malformed XML", operation);
            throw new BillingException.DianUnavailable();
        }
        List<Element> results = descendants(document.getDocumentElement(), operation + "Result");
        if (results.isEmpty()) {
            log.warn("The DIAN answered {} without {}Result", operation, operation);
            throw new BillingException.DianUnavailable();
        }
        return results.getFirst();
    }

    private static String fault(byte[] body) {
        try {
            String reason = text(XmlSupport.parse(body).getDocumentElement(), "Text");
            return reason == null ? "sin detalle" : reason;
        } catch (RuntimeException unreadable) {
            return "sin detalle";
        }
    }

    private static String text(Element parent, String localName) {
        List<Element> found = descendants(parent, localName);
        if (found.isEmpty()) {
            return null;
        }
        Element element = found.getFirst();
        if ("true".equals(element.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "nil"))) {
            return null;
        }
        String value = element.getTextContent().strip();
        return value.isEmpty() ? null : value;
    }

    private static List<Element> descendants(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        List<Element> elements = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            elements.add((Element) nodes.item(i));
        }
        return elements;
    }

    private static List<Element> children(Element parent) {
        List<Element> elements = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }
}
