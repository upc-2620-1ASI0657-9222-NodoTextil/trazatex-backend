package com.nodotextil.trazatex.production.acceptance;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/** Cloudinary simulado con WireMock: el adaptador real le habla por HTTP (ADR-007). */
public final class CloudinaryStub {

    public static final String UPLOAD_PATH = "/v1_1/demo/image/upload";
    public static final WireMockServer SERVER = new WireMockServer(options().dynamicPort().globalTemplating(true));

    static {
        SERVER.start();
        Runtime.getRuntime().addShutdownHook(new Thread(SERVER::stop));
    }

    private CloudinaryStub() {
    }

    /** Responde 200 con la URL de la imagen, que lleva el {@code public_id} recibido. */
    public static void reset() {
        SERVER.resetAll();
        SERVER.stubFor(post(urlPathEqualTo(UPLOAD_PATH)).willReturn(okJson(
                "{\"public_id\":\"{{request.parts.public_id.body}}\",\"secure_url\":\"https://res.cloudinary.com/demo/image/upload/v1/{{request.parts.public_id.body}}.jpg\"}")
                .withTransformers("response-template")));
    }

    public static List<LoggedRequest> uploads() {
        return SERVER.findAll(postRequestedFor(urlPathEqualTo(UPLOAD_PATH)));
    }
}
