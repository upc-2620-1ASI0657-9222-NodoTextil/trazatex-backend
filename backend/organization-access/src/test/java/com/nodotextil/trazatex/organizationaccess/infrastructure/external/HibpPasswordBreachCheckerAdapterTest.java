package com.nodotextil.trazatex.organizationaccess.infrastructure.external;

import com.nodotextil.trazatex.organizationaccess.application.port.out.PasswordBreachCheckerPort.Result;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-007 con WireMock. SHA-1("password") = 5BAA6 1E4C9B93F3F0682250B6CF8331B7EE68FD8: a HIBP solo viaja el prefijo "5BAA6".
 */
class HibpPasswordBreachCheckerAdapterTest {

    @RegisterExtension
    static WireMockExtension hibp = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final String PASSWORD = "password";
    private static final String PREFIX = "5BAA6";
    private static final String SUFFIX = "1E4C9B93F3F0682250B6CF8331B7EE68FD8";

    private CircuitBreaker breaker;

    private HibpPasswordBreachCheckerAdapter adapter(boolean enabled, Duration timeout, CircuitBreakerSettings settings) {
        breaker = CircuitBreakerRegistry.ofDefaults().circuitBreaker("hibp", settings.toConfig());
        return new HibpPasswordBreachCheckerAdapter(ExternalHttpClients.restClient(hibp.baseUrl(), timeout), breaker, enabled);
    }

    private HibpPasswordBreachCheckerAdapter adapter() {
        return adapter(true, Duration.ofSeconds(2), new CircuitBreakerSettings(10, 5, 50, Duration.ofSeconds(30), 2));
    }

    private static void stubRange(String body) {
        hibp.stubFor(get(urlEqualTo("/range/" + PREFIX)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "text/plain").withBody(body)));
    }

    @Test
    void aLeakedPasswordIsCompromised() {
        stubRange("0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n" + SUFFIX + ":3861493\r\n011053FD0102E94D6AE2F8B83D76FAF94F6:1\r\n");

        assertThat(adapter().check(PASSWORD)).isEqualTo(Result.COMPROMISED);
    }

    @Test
    void aPasswordWhoseSuffixIsNotInTheRangeIsClean() {
        stubRange("0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n011053FD0102E94D6AE2F8B83D76FAF94F6:7\r\n");

        assertThat(adapter().check(PASSWORD)).isEqualTo(Result.CLEAN);
    }

    @Test
    void paddingEntriesWithZeroCountAreNotALeak() {
        stubRange("0018A45C4D1DEF81644B54AB7F969B88D65:1\n" + SUFFIX + ":0\n");

        assertThat(adapter().check(PASSWORD)).isEqualTo(Result.CLEAN);
    }

    @Test
    void theSuffixComparisonIgnoresCase() {
        stubRange(SUFFIX.toLowerCase() + ":12\n");

        assertThat(adapter().check(PASSWORD)).isEqualTo(Result.COMPROMISED);
    }

    @Test
    void onlyTheFiveCharacterPrefixLeavesTheServiceAndPaddingIsRequested() {
        stubRange(SUFFIX + ":5\n");

        adapter().check(PASSWORD);

        hibp.verify(1, getRequestedFor(urlEqualTo("/range/" + PREFIX)).withHeader("Add-Padding", equalTo("true")));
        assertThat(hibp.getAllServeEvents()).hasSize(1).allSatisfy(event -> {
            String request = event.getRequest().getUrl() + " " + event.getRequest().getHeaders() + " " + event.getRequest().getBodyAsString();
            assertThat(request).doesNotContain(PASSWORD).doesNotContain(SUFFIX).doesNotContainIgnoringCase(SUFFIX.substring(0, 20));
        });
    }

    @Test
    void ifTheServiceIsSlowItIsUnavailableNotAnError() {
        hibp.stubFor(get(urlEqualTo("/range/" + PREFIX)).willReturn(aResponse().withStatus(200).withBody(SUFFIX + ":5").withFixedDelay(3000)));
        long start = System.nanoTime();

        Result result = adapter(true, Duration.ofMillis(300), new CircuitBreakerSettings(10, 5, 50, Duration.ofSeconds(30), 2)).check(PASSWORD);

        assertThat(result).isEqualTo(Result.UNAVAILABLE);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2000));
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 429, 403, 404})
    void anErrorStatusIsUnavailable(int status) {
        hibp.stubFor(get(urlEqualTo("/range/" + PREFIX)).willReturn(aResponse().withStatus(status)));

        assertThat(adapter().check(PASSWORD)).isEqualTo(Result.UNAVAILABLE);
    }

    @Test
    void aBrokenConnectionOrMalformedAnswerIsUnavailable() {
        hibp.stubFor(get(urlEqualTo("/range/" + PREFIX)).willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));
        assertThat(adapter().check(PASSWORD)).isEqualTo(Result.UNAVAILABLE);

        stubRange(SUFFIX + ":no-es-un-numero\n");
        assertThat(adapter().check(PASSWORD)).isEqualTo(Result.UNAVAILABLE);
    }

    @Test
    void afterRepeatedFailuresTheCircuitOpensAndTheServiceIsNotCalledAgain() throws Exception {
        HibpPasswordBreachCheckerAdapter adapter = adapter(true, Duration.ofSeconds(2), new CircuitBreakerSettings(4, 4, 50, Duration.ofMillis(300), 1));
        hibp.stubFor(get(urlEqualTo("/range/" + PREFIX)).willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 4; i++) {
            assertThat(adapter.check(PASSWORD)).isEqualTo(Result.UNAVAILABLE);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(adapter.check(PASSWORD)).isEqualTo(Result.UNAVAILABLE);
        hibp.verify(4, getRequestedFor(urlEqualTo("/range/" + PREFIX)));

        Thread.sleep(400);
        stubRange(SUFFIX + ":5\n");
        assertThat(adapter.check(PASSWORD)).isEqualTo(Result.COMPROMISED);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void whenDisabledItNeverCallsTheServiceAndTheCheckPasses() {
        stubRange(SUFFIX + ":5\n");

        assertThat(adapter(false, Duration.ofSeconds(2), new CircuitBreakerSettings(10, 5, 50, Duration.ofSeconds(30), 2)).check(PASSWORD))
                .isEqualTo(Result.CLEAN);

        assertThat(hibp.getAllServeEvents()).isEmpty();
    }
}
