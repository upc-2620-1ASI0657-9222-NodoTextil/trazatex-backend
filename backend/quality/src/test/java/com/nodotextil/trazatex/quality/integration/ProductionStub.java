package com.nodotextil.trazatex.quality.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/** Un Production simulado para toda la JVM de pruebas: el comando interno {@code /internal/batches/{id}/phase-transitions}. */
public final class ProductionStub {

    public static final WireMockServer SERVER = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        SERVER.start();
        Runtime.getRuntime().addShutdownHook(new Thread(SERVER::stop));
    }

    private ProductionStub() {
    }

    public static String baseUrl() {
        return "http://localhost:" + SERVER.port();
    }

    public static void reset() {
        SERVER.resetAll();
    }

    public static String apiError(String code) {
        return "{\"code\":\"" + code + "\",\"message\":\"mensaje de " + code + "\",\"details\":[],\"timestamp\":\"2026-10-23T12:00:00Z\"}";
    }

    /** Production aplica cualquier comando: 200 con la fase siguiente de {@code START_EVALUATION}. */
    public static void answersOk() {
        SERVER.stubFor(post(urlPathMatching("/internal/batches/.*/phase-transitions")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"batchId\":\"00000000-0000-0000-0000-000000000000\",\"phase\":\"IN_QUALITY_CONTROL\",\"qualityHold\":false}")));
    }

    public static void answers(int status, String body) {
        SERVER.stubFor(post(urlPathMatching("/internal/batches/.*/phase-transitions")).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    public static List<LoggedRequest> requestsFor(UUID batchId) {
        return SERVER.findAll(postRequestedFor(urlPathEqualTo("/internal/batches/" + batchId + "/phase-transitions")));
    }

    public static List<LoggedRequest> allRequests() {
        return SERVER.findAll(postRequestedFor(urlPathMatching("/internal/batches/.*/phase-transitions")));
    }
}
