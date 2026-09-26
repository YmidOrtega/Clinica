package com.ClinicaDeYmid.billing_service.support;

import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

public final class MinistrySimulator {

    public static final String PATH = "/muv";
    public static final String TOKEN = "muv-token-1";
    public static final String CUV = "a2cf8c6b2f9563d39c5cd7dd8d73e77a5926165a269c07ee84a1c4162ecd198526501a93c1b2f8155ddaf88c82fb15b4";

    private MinistrySimulator() {
    }

    public static String url() {
        return StubbedServices.baseUrl() + PATH;
    }

    public static void logsIn() {
        StubbedServices.server().stubFor(WireMock.post(urlPathEqualTo(PATH + "/api/Auth/LoginSISPRO"))
                .willReturn(okJson("{\"token\":\"" + TOKEN + "\",\"login\":true,\"registrado\":true,\"errors\":[]}")));
    }

    public static void validates(String invoiceNumber) {
        submit(okJson(result(true, invoiceNumber, CUV, """
                {"Clase":"NOTIFICACION","Codigo":"FED078","Descripcion":"Aviso de prueba","Observaciones":"",
                 "PathFuente":"","Fuente":"FacturaElectronica"}""")));
    }

    public static void rejects(String invoiceNumber, String code, String description) {
        submit(aResponse().withStatus(400).withHeader("Content-Type", "application/json").withBody(
                result(false, invoiceNumber, null, """
                        {"Clase":"RECHAZADO","Codigo":"%s","Descripcion":"%s","Observaciones":"",
                         "PathFuente":"usuarios[0]","Fuente":"Rips"}""".formatted(code, description))));
    }

    public static void alreadyValidated(String invoiceNumber) {
        submit(aResponse().withStatus(400).withHeader("Content-Type", "application/json").withBody(
                result(false, invoiceNumber, null, """
                        {"Clase":"RECHAZADO","Codigo":"RVG18","Descripcion":"La factura ya fue validada",
                         "Observaciones":"CUV %s","PathFuente":"","Fuente":"FacturaElectronica"}""".formatted(CUV))));
        StubbedServices.server().stubFor(WireMock.post(urlPathEqualTo(PATH + "/api/ConsultasFevRips/RecuperarCUV"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .willReturn(okJson(result(true, invoiceNumber, CUV, ""))));
    }

    public static void isDown() {
        submit(aResponse().withStatus(503));
    }

    private static void submit(com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder response) {
        StubbedServices.server().stubFor(WireMock.post(urlPathEqualTo(PATH + "/api/PaquetesFevRips/CargarFevRips"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .willReturn(response));
    }

    private static String result(boolean state, String invoiceNumber, String cuv, String findings) {
        return """
                {"ResultState":%s,"ProcesoId":1024,"NumFactura":"%s","CodigoUnicoValidacion":%s,
                 "FechaRadicacion":"2026-09-26T17:25:54.7705162+00:00","RutaArchivos":null,
                 "ResultadosValidacion":[%s]}""".formatted(state, invoiceNumber,
                cuv == null ? "null" : "\"" + cuv + "\"", findings);
    }
}
