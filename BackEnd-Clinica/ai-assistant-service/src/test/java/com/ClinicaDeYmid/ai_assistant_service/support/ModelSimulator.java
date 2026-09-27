package com.ClinicaDeYmid.ai_assistant_service.support;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

public final class ModelSimulator {

    public static final String COMPLETIONS = "/v1/chat/completions";

    private ModelSimulator() {
    }

    public static void answers(String text) {
        StubbedServices.server().stubFor(WireMock.post(urlPathEqualTo(COMPLETIONS)).willReturn(okJson(reply(text))));
    }

    public static void callsATool(String tool, String arguments, String thenAnswers) {
        StubbedServices.server().stubFor(WireMock.post(urlPathEqualTo(COMPLETIONS)).inScenario("tool")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson("""
                        {"id":"c1","object":"chat.completion","created":1,"model":"local","choices":[{"index":0,
                          "message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function",
                          "function":{"name":"%s","arguments":%s}}]},"finish_reason":"tool_calls"}],
                         "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}"""
                        .formatted(tool, quoted(arguments))))
                .willSetStateTo("answered"));
        StubbedServices.server().stubFor(WireMock.post(urlPathEqualTo(COMPLETIONS)).inScenario("tool")
                .whenScenarioStateIs("answered")
                .willReturn(okJson(reply(thenAnswers))));
    }

    public static void fails() {
        StubbedServices.server().stubFor(WireMock.post(urlPathEqualTo(COMPLETIONS))
                .willReturn(aResponse().withStatus(500).withBody("model crashed")));
    }

    public static void takes(int millis) {
        StubbedServices.server().stubFor(WireMock.post(urlPathEqualTo(COMPLETIONS))
                .willReturn(okJson(reply("tarde")).withFixedDelay(millis)));
    }

    private static String reply(String text) {
        return """
                {"id":"c2","object":"chat.completion","created":1,"model":"local","choices":[{"index":0,
                  "message":{"role":"assistant","content":%s},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}""".formatted(quoted(text));
    }

    private static String quoted(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
