package com.nodotextil.trazatex.organizationaccess.infrastructure.external;

import com.nodotextil.trazatex.organizationaccess.application.dto.CachedRucValidation;
import com.nodotextil.trazatex.organizationaccess.application.exception.ExternalServiceUnavailableException;
import com.nodotextil.trazatex.organizationaccess.domain.model.Ruc;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import com.nodotextil.trazatex.sharedkernel.testsupport.MutableClock;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.nodotextil.trazatex.organizationaccess.infrastructure.external.ChequeaStubs.RUC;
import static com.nodotextil.trazatex.organizationaccess.infrastructure.external.ChequeaStubs.forRuc;
import static com.nodotextil.trazatex.organizationaccess.infrastructure.external.ChequeaStubs.json;
import static com.nodotextil.trazatex.organizationaccess.infrastructure.external.ChequeaStubs.notFound;
import static com.nodotextil.trazatex.organizationaccess.infrastructure.external.ChequeaStubs.problem;
import static com.nodotextil.trazatex.organizationaccess.infrastructure.external.ChequeaStubs.required;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADR-012 con WireMock. Nunca se llama a la API real. */
class ChequeaRucValidatorAdapterTest {

    @RegisterExtension
    static WireMockExtension chequea = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private static final String TOKEN = "token-de-prueba";

    private final InMemoryRucValidationCache cache = new InMemoryRucValidationCache();
    private final MutableClock clock = new MutableClock(NOW);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private CircuitBreaker breaker;
    private ChequeaRucValidatorAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = adapter(Duration.ofSeconds(3), new CircuitBreakerSettings(10, 5, 50, Duration.ofSeconds(30), 2), TOKEN);
    }

    private ChequeaRucValidatorAdapter adapter(Duration timeout, CircuitBreakerSettings settings, String token) {
        breaker = CircuitBreakerRegistry.ofDefaults().circuitBreaker("chequea", settings.toConfig());
        return new ChequeaRucValidatorAdapter(ExternalHttpClients.restClient(chequea.baseUrl(), timeout), breaker, cache, mapper,
                token, Duration.ofHours(24), clock);
    }

    private static Ruc ruc() {
        return new Ruc(RUC);
    }

    // ---- Los cuatro resultados de negocio ----

    @Test
    void activoYHabidoSeAceptaSeEnvíaElBearerYSeGuardaEnCaché() {
        chequea.stubFor(forRuc(RUC).willReturn(json(200, required("ruc-activo-habido.json"))));

        assertThatCode(() -> adapter.ensureActiveAndHabido(ruc())).doesNotThrowAnyException();

        chequea.verify(1, getRequestedFor(urlEqualTo("/api/v1/ruc/" + RUC))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .withHeader("Accept", equalTo("application/json")));
        CachedRucValidation saved = cache.entries.get(RUC);
        assertThat(saved.businessName()).startsWith("SUPERINTENDENCIA NACIONAL DE ADUANAS");
        assertThat(saved.taxpayerStatus()).isEqualTo("ACTIVO");
        assertThat(saved.domicileCondition()).isEqualTo("HABIDO");
        assertThat(saved.validatedAt()).isEqualTo(NOW);
    }

    @Test
    void bajaSeRechazaConRucInvalidoYNoSeCachea() {
        chequea.stubFor(forRuc(RUC).willReturn(json(200, required("ruc-baja.json"))));

        assertThatThrownBy(() -> adapter.ensureActiveAndHabido(ruc()))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo("RUC_INVALID");
                    assertThat(e.getMessage()).contains("ACTIVO y HABIDO").contains("BAJA");
                });
        assertThat(cache.entries).isEmpty();
    }

    @Test
    void noHabidoSeRechazaConRucInvalidoYNoSeCachea() {
        chequea.stubFor(forRuc(RUC).willReturn(json(200, required("ruc-no-habido.json"))));

        assertThatThrownBy(() -> adapter.ensureActiveAndHabido(ruc()))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo("RUC_INVALID");
                    assertThat(e.getMessage()).contains("NO HABIDO");
                });
        assertThat(cache.entries).isEmpty();
    }

    @Test
    void rucQueNoExisteResponde404YSeRechazaComoInvalido() {
        chequea.stubFor(forRuc(RUC).willReturn(notFound()));

        assertThatThrownBy(() -> adapter.ensureActiveAndHabido(ruc()))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo("RUC_INVALID");
                    assertThat(e.getMessage()).contains("no existe");
                });
        assertThat(cache.entries).isEmpty();
    }

    @Test
    void unaRespuesta400ProblemJsonDeLaApiSeRechazaComoInvalida() {
        // El prefijo y el módulo 11 ya se validan en Ruc, así que un 400 solo llega si la API cambia sus reglas: se rechaza igual
        chequea.stubFor(forRuc(RUC).willReturn(problem(400, required("ruc-formato-invalido.json"))));

        assertThatThrownBy(() -> adapter.ensureActiveAndHabido(ruc()))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.code()).isEqualTo("RUC_INVALID"));
        assertThat(cache.entries).isEmpty();
    }

    @Test
    void unRucConPrefijoInvalidoNiSiquieraLlegaAlAdaptador() {
        String prefix = "3010007097";
        assertThatThrownBy(() -> new Ruc(prefix + Ruc.checkDigitFor(prefix))).isInstanceOf(ValidationException.class);
        chequea.verify(0, getRequestedFor(urlMatching("/api/v1/ruc/.*")));
    }

    @Test
    void lasVariantesBajaYNoHabidoSonElArchivoRealCambiandoSoloEstadoOCondicion() throws Exception {
        JsonNode real = mapper.readTree(required("ruc-activo-habido.json"));
        JsonNode baja = mapper.readTree(required("ruc-baja.json"));
        JsonNode noHabido = mapper.readTree(required("ruc-no-habido.json"));

        assertThat(real.get("estado").asString()).isEqualTo("ACTIVO");
        assertThat(real.get("condicion").asString()).isEqualTo("HABIDO");
        assertThat(baja.get("estado").asString()).isEqualTo("BAJA");
        assertThat(noHabido.get("condicion").asString()).isEqualTo("NO HABIDO");
        for (String field : real.propertyNames()) {
            if (!field.equals("estado")) {
                assertThat(baja.get(field)).as("baja.%s", field).isEqualTo(real.get(field));
            }
            if (!field.equals("condicion")) {
                assertThat(noHabido.get(field)).as("noHabido.%s", field).isEqualTo(real.get(field));
            }
        }
        assertThat(baja.get("condicion")).isEqualTo(real.get("condicion"));
        assertThat(noHabido.get("estado")).isEqualTo(real.get("estado"));
        assertThat(real.propertyNames()).containsExactlyInAnyOrder("ruc", "razonSocial", "estado", "condicion", "tipo", "ubigeo",
                "direccion", "ciiu", "actualizado");
    }

    // ---- Fallos del servicio: nunca se acepta un RUC sin validar ----

    @Test
    void siLaApiTardaMásDelTimeoutSeRechazaConMensajeDeReintentoSinEsperarLaRespuesta() {
        adapter = adapter(Duration.ofMillis(300), new CircuitBreakerSettings(10, 5, 50, Duration.ofSeconds(30), 2), TOKEN);
        chequea.stubFor(forRuc(RUC).willReturn(json(200, required("ruc-activo-habido.json")).withFixedDelay(3000)));

        long start = System.nanoTime();
        assertThatThrownBy(() -> adapter.ensureActiveAndHabido(ruc()))
                .isInstanceOfSatisfying(ExternalServiceUnavailableException.class, e -> {
                    assertThat(e.code()).isEqualTo("RUC_VALIDATION_UNAVAILABLE");
                    assertThat(e.getMessage()).contains("Inténtalo más tarde");
                });
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2000));
        assertThat(cache.entries).isEmpty();
    }

    @Test
    void siLaApiRespondeLosEncabezadosYSeDetieneTambienSeCorta() {
        adapter = adapter(Duration.ofMillis(300), new CircuitBreakerSettings(10, 5, 50, Duration.ofSeconds(30), 2), TOKEN);
        chequea.stubFor(forRuc(RUC).willReturn(json(200, required("ruc-activo-habido.json")).withChunkedDribbleDelay(5, 4000)));

        long start = System.nanoTime();
        assertThatThrownBy(() -> adapter.ensureActiveAndHabido(ruc())).isInstanceOf(ExternalServiceUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2500));
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 429, 401, 403, 418})
    void unCódigoDeErrorDelServicioEsApiNoDisponible(int status) {
        chequea.stubFor(forRuc(RUC).willReturn(aResponse().withStatus(status)));

        assertThatThrownBy(() -> adapter.ensureActiveAndHabido(ruc())).isInstanceOf(ExternalServiceUnavailableException.class);
        assertThat(cache.entries).isEmpty();
    }

    @Test
    void unaRespuestaIncomprensibleNuncaSeAcepta() {
        for (String body : new String[]{"no es json", "{}", "{\"ruc\":\"" + RUC + "\"}", "[]",
                required("ruc-activo-habido.json").replace(RUC, "20100070970"),
                required("ruc-activo-habido.json").replace("\"estado\":\"ACTIVO\"", "\"estado\":null")}) {
            chequea.stubFor(forRuc(RUC).willReturn(json(200, body)));
            assertThatThrownBy(() -> adapter.ensureActiveAndHabido(ruc())).isInstanceOf(ExternalServiceUnavailableException.class);
        }
        assertThat(cache.entries).isEmpty();
    }

    @Test
    void siLaConexiónSeCortaOLaApiNoEstáSeRechazaConReintento() {
        chequea.stubFor(forRuc(RUC).willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));
        assertThatThrownBy(() -> adapter.ensureActiveAndHabido(ruc())).isInstanceOf(ExternalServiceUnavailableException.class);

        chequea.stubFor(forRuc(RUC).willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.MALFORMED_RESPONSE_CHUNK)));
        assertThatThrownBy(() -> adapter.ensureActiveAndHabido(ruc())).isInstanceOf(ExternalServiceUnavailableException.class);
    }

    @Test
    void sinTokenNoSeLlamaALaApiYNoSeAceptaNingúnRucSinCaché() {
        adapter = adapter(Duration.ofSeconds(3), new CircuitBreakerSettings(10, 5, 50, Duration.ofSeconds(30), 2), "  ");
        chequea.stubFor(forRuc(RUC).willReturn(json(200, required("ruc-activo-habido.json"))));

        assertThatThrownBy(() -> adapter.ensureActiveAndHabido(ruc())).isInstanceOf(ExternalServiceUnavailableException.class);

        chequea.verify(0, getRequestedFor(urlEqualTo("/api/v1/ruc/" + RUC)));
    }

    // ---- Caché (ADR-012: dígito verificador → caché → API) ----

    private void cacheValidatedAt(Instant validatedAt) {
        cache.save(new CachedRucValidation(ruc(), "SUNAT", "ACTIVO", "HABIDO", validatedAt));
    }

    @Test
    void unaValidaciónRecienteEnCachéSeUsaSinLlamarALaApi() {
        cacheValidatedAt(NOW.minus(Duration.ofHours(23)));

        assertThatCode(() -> adapter.ensureActiveAndHabido(ruc())).doesNotThrowAnyException();

        chequea.verify(0, getRequestedFor(urlEqualTo("/api/v1/ruc/" + RUC)));
    }

    @Test
    void unaValidaciónAntiguaSeVuelveAConsultarYSeRenueva() {
        cacheValidatedAt(NOW.minus(Duration.ofHours(25)));
        chequea.stubFor(forRuc(RUC).willReturn(json(200, required("ruc-activo-habido.json"))));

        adapter.ensureActiveAndHabido(ruc());

        chequea.verify(1, getRequestedFor(urlEqualTo("/api/v1/ruc/" + RUC)));
        assertThat(cache.entries.get(RUC).validatedAt()).isEqualTo(NOW);
    }

    @Test
    void siLaApiNoRespondeSeUsaLaValidaciónEnCachéAunqueSeaAntigua() {
        cacheValidatedAt(NOW.minus(Duration.ofDays(40)));
        chequea.stubFor(forRuc(RUC).willReturn(aResponse().withStatus(503)));

        assertThatCode(() -> adapter.ensureActiveAndHabido(ruc())).doesNotThrowAnyException();

        chequea.verify(1, getRequestedFor(urlEqualTo("/api/v1/ruc/" + RUC)));
        assertThat(cache.entries.get(RUC).validatedAt()).as("el respaldo no renueva la fecha").isEqualTo(NOW.minus(Duration.ofDays(40)));
    }

    @Test
    void siLaApiDiceQueYaNoEsActivoSeRechazaYSeBorraLaEntradaDeCaché() {
        cacheValidatedAt(NOW.minus(Duration.ofHours(30)));
        chequea.stubFor(forRuc(RUC).willReturn(json(200, required("ruc-baja.json"))));

        assertThatThrownBy(() -> adapter.ensureActiveAndHabido(ruc())).isInstanceOf(ValidationException.class);

        assertThat(cache.entries).isEmpty();
    }

    // ---- Registro de la causa (el usuario ve lo mismo en todos los casos) ----

    private ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> captureLogs() {
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ChequeaRucValidatorAdapter.class)).addAppender(appender);
        return appender;
    }

    private void stopCapturing(ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender) {
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ChequeaRucValidatorAdapter.class)).detachAppender(appender);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void unTokenRechazadoSeRegistraComoErrorConLaCausaYElUsuarioVeLoMismoDeSiempre(int status) {
        var logs = captureLogs();
        try {
            chequea.stubFor(forRuc(RUC).willReturn(aResponse().withStatus(status)));

            assertThatThrownBy(() -> adapter.ensureActiveAndHabido(ruc()))
                    .isInstanceOfSatisfying(ExternalServiceUnavailableException.class, e -> assertThat(e.code()).isEqualTo("RUC_VALIDATION_UNAVAILABLE"));

            assertThat(logs.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.ERROR);
                assertThat(event.getFormattedMessage()).contains("Token de Chequea.pe inválido o vencido, revisar CHEQUEA_API_TOKEN");
            });
            assertThat(logs.list).extracting(event -> event.getFormattedMessage()).noneMatch(message -> message.contains(TOKEN));
        } finally {
            stopCapturing(logs);
        }
    }

    @Test
    void laCuotaAgotadaSeRegistraComoWarnYElUsuarioVeLoMismoDeSiempre() {
        var logs = captureLogs();
        try {
            chequea.stubFor(forRuc(RUC).willReturn(aResponse().withStatus(429)));

            assertThatThrownBy(() -> adapter.ensureActiveAndHabido(ruc()))
                    .isInstanceOfSatisfying(ExternalServiceUnavailableException.class, e -> assertThat(e.code()).isEqualTo("RUC_VALIDATION_UNAVAILABLE"));

            assertThat(logs.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
                assertThat(event.getFormattedMessage()).contains("Cuota de Chequea.pe agotada");
            });
            assertThat(logs.list).noneMatch(event -> event.getLevel() == ch.qos.logback.classic.Level.ERROR);
        } finally {
            stopCapturing(logs);
        }
    }

    @Test
    void unErrorDelServidorNoSeConfundeConUnTokenInvalidoNiConLaCuota() {
        var logs = captureLogs();
        try {
            chequea.stubFor(forRuc(RUC).willReturn(aResponse().withStatus(503)));

            assertThatThrownBy(() -> adapter.ensureActiveAndHabido(ruc())).isInstanceOf(ExternalServiceUnavailableException.class);

            assertThat(logs.list).extracting(event -> event.getFormattedMessage())
                    .noneMatch(message -> message.contains("Token de Chequea.pe") || message.contains("Cuota de Chequea.pe"));
            assertThat(logs.list).noneMatch(event -> event.getLevel() == ch.qos.logback.classic.Level.ERROR);
        } finally {
            stopCapturing(logs);
        }
    }

    // ---- Circuit breaker ----

    @Test
    void trasVariosFallosElCircuitoSeAbreYNoSeVuelveALlamarALaApiHastaQueSeRecupera() throws Exception {
        adapter = adapter(Duration.ofSeconds(3), new CircuitBreakerSettings(4, 4, 50, Duration.ofMillis(300), 1), TOKEN);
        chequea.stubFor(forRuc(RUC).willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> adapter.ensureActiveAndHabido(ruc())).isInstanceOf(ExternalServiceUnavailableException.class);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> adapter.ensureActiveAndHabido(ruc())).isInstanceOf(ExternalServiceUnavailableException.class);
        chequea.verify(4, getRequestedFor(urlEqualTo("/api/v1/ruc/" + RUC)));

        Thread.sleep(400);
        chequea.stubFor(forRuc(RUC).willReturn(json(200, required("ruc-activo-habido.json"))));
        assertThatCode(() -> adapter.ensureActiveAndHabido(ruc())).doesNotThrowAnyException();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        chequea.verify(5, getRequestedFor(urlEqualTo("/api/v1/ruc/" + RUC)));
    }

    @Test
    void conElCircuitoAbiertoUnRucEnCachéSigueValidándose() {
        adapter = adapter(Duration.ofSeconds(3), new CircuitBreakerSettings(4, 4, 50, Duration.ofSeconds(30), 1), TOKEN);
        cacheValidatedAt(NOW.minus(Duration.ofDays(3)));
        chequea.stubFor(forRuc(RUC).willReturn(aResponse().withStatus(500)));
        breaker.transitionToOpenState();

        assertThatCode(() -> adapter.ensureActiveAndHabido(ruc())).doesNotThrowAnyException();

        chequea.verify(0, getRequestedFor(urlEqualTo("/api/v1/ruc/" + RUC)));
    }

    @Test
    void lasRespuestas400y404NoCuentanComoFalloDelServicio() {
        adapter = adapter(Duration.ofSeconds(3), new CircuitBreakerSettings(4, 4, 50, Duration.ofSeconds(30), 1), TOKEN);
        chequea.stubFor(forRuc(RUC).willReturn(notFound()));

        for (int i = 0; i < 12; i++) {
            assertThatThrownBy(() -> adapter.ensureActiveAndHabido(ruc())).isInstanceOf(ValidationException.class);
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        chequea.verify(12, getRequestedFor(urlEqualTo("/api/v1/ruc/" + RUC)));
    }

    // ---- El token no se filtra ----

    @Test
    void elTokenNoApareceEnLasPropiedadesImpresas() {
        ChequeaProperties properties = new ChequeaProperties("https://api.chequea.pe", "secreto-123", Duration.ofSeconds(3),
                Duration.ofHours(24), new CircuitBreakerSettings(10, 5, 50, Duration.ofSeconds(30), 2));
        assertThat(properties.toString()).doesNotContain("secreto-123").contains("****");
    }
}
