package com.ClinicaDeYmid.billing_service.support;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

public final class DianSimulator {

    public static final String PATH = "/dian/WcfDianCustomerServices.svc";
    public static final String APPLICATION_RESPONSE =
            "<ApplicationResponse xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2\">"
                    + "<Response>02</Response></ApplicationResponse>";

    private DianSimulator() {
    }

    public static String url() {
        return StubbedServices.baseUrl() + PATH;
    }

    public static void receivesTheTestSet(String zipKey) {
        answer("SendTestSetAsync", """
                <SendTestSetAsyncResult xmlns:b="http://schemas.datacontract.org/2004/07/UploadDocumentResponse"
                    xmlns:i="http://www.w3.org/2001/XMLSchema-instance">
                  <b:ErrorMessageList xmlns:c="http://schemas.datacontract.org/2004/07/XmlParamsResponseTrackId"/>
                  <b:ZipKey>%s</b:ZipKey>
                </SendTestSetAsyncResult>""".formatted(zipKey));
    }

    public static void refusesTheUpload(String message) {
        answer("SendTestSetAsync", """
                <SendTestSetAsyncResult xmlns:b="http://schemas.datacontract.org/2004/07/UploadDocumentResponse"
                    xmlns:i="http://www.w3.org/2001/XMLSchema-instance">
                  <b:ErrorMessageList xmlns:c="http://schemas.datacontract.org/2004/07/XmlParamsResponseTrackId">
                    <c:XmlParamsResponseTrackId>
                      <c:DocumentKey i:nil="true"/>
                      <c:ProcessedMessage>%s</c:ProcessedMessage>
                      <c:Success>false</c:Success>
                    </c:XmlParamsResponseTrackId>
                  </b:ErrorMessageList>
                  <b:ZipKey i:nil="true"/>
                </SendTestSetAsyncResult>""".formatted(message));
    }

    public static void validates(String operation) {
        answer(operation, result(operation, dianResponse("true", "00", "Procesado Correctamente.",
                Base64.getEncoder().encodeToString(APPLICATION_RESPONSE.getBytes(StandardCharsets.UTF_8)))));
    }

    public static void isStillProcessing() {
        answer("GetStatusZip", result("GetStatusZip", dianResponse("false", "", "Batch en proceso de validación.", null)));
    }

    public static void rejects(String operation, String... errors) {
        answer(operation, result(operation, dianResponse("false", "99",
                "Validación contiene errores en campos mandatorios.", null, errors)));
    }

    public static void doesNotKnowTheDocument() {
        answer("GetStatus", result("GetStatus", dianResponse("false", "66", "NSU no encontrado", null)));
    }

    public static void isDown() {
        StubbedServices.server().stubFor(WireMock.post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(500)
                .withHeader("Content-Type", "application/soap+xml; charset=utf-8")
                .withBody("""
                        <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"><s:Body><s:Fault>
                        <s:Code><s:Value>s:Receiver</s:Value></s:Code>
                        <s:Reason><s:Text xml:lang="es-CO">Servicio no disponible</s:Text></s:Reason>
                        </s:Fault></s:Body></s:Envelope>""")));
    }

    private static String result(String operation, String content) {
        return """
                <%sResult xmlns:b="http://schemas.datacontract.org/2004/07/DianResponse"
                    xmlns:i="http://www.w3.org/2001/XMLSchema-instance">%s</%sResult>"""
                .formatted(operation, operation.equals("GetStatusZip") ? content : unwrap(content), operation);
    }

    private static String unwrap(String dianResponse) {
        return dianResponse.replace("<b:DianResponse>", "").replace("</b:DianResponse>", "");
    }

    private static String dianResponse(String valid, String code, String description, String base64,
                                       String... errors) {
        String messages = Arrays.stream(errors).map(error -> "<c:string>" + error + "</c:string>")
                .collect(Collectors.joining());
        return """
                <b:DianResponse>
                  <b:ErrorMessage xmlns:c="http://schemas.microsoft.com/2003/10/Serialization/Arrays">%s</b:ErrorMessage>
                  <b:IsValid>%s</b:IsValid>
                  <b:StatusCode>%s</b:StatusCode>
                  <b:StatusDescription>%s</b:StatusDescription>
                  <b:StatusMessage>%s</b:StatusMessage>
                  %s
                  <b:XmlDocumentKey>cufe</b:XmlDocumentKey>
                </b:DianResponse>""".formatted(messages, valid, code, description, description,
                base64 == null ? "<b:XmlBase64Bytes i:nil=\"true\"/>" : "<b:XmlBase64Bytes>" + base64 + "</b:XmlBase64Bytes>");
    }

    private static void answer(String operation, String result) {
        MappingBuilder request = WireMock.post(urlPathEqualTo(PATH))
                .withHeader("Content-Type", containing("IWcfDianCustomerServices/" + operation + "\""))
                .withRequestBody(containing("<wsse:BinarySecurityToken"));
        StubbedServices.server().stubFor(request.willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/soap+xml; charset=utf-8")
                .withBody("""
                        <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
                            xmlns:a="http://www.w3.org/2005/08/addressing">
                          <s:Header><a:Action s:mustUnderstand="1">http://wcf.dian.colombia/IWcfDianCustomerServices/%sResponse</a:Action></s:Header>
                          <s:Body><%sResponse xmlns="http://wcf.dian.colombia">%s</%sResponse></s:Body>
                        </s:Envelope>""".formatted(operation, operation, result, operation))));
    }
}
