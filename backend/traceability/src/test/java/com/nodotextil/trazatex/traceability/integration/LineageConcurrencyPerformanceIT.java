package com.nodotextil.trazatex.traceability.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RNF-04: p95 ≤ 5 s con 100 consultas simultáneas sobre un grafo de ≈500 nodos con mezclas y divisiones (en forma de diamante), por HTTP real contra
 * traceability (servidor embebido en un puerto, cliente {@code java.net.http}, sin pasar por el Gateway: la medición principal es del servicio) y con Neo4j con
 * la misma memoria que el compose (heap de 512 MB, caché de 256 MB: {@code TrazatexNeo4jContainer}).
 *
 * <p>Cada <b>oleada</b> lanza 100 peticiones en el mismo instante (todas esperan un cerrojo y salen juntas); se hacen varias oleadas de calentamiento (no se
 * miden: JIT, conexiones, estadísticas de Neo4j) y luego 10 medidas, 1000 peticiones en total. Cada petición mide de punta a punta en el cliente (desde que
 * se envía hasta tener el cuerpo completo). La dirección (ancestros, descendientes, completa) y el lote de partida varían entre peticiones y entre oleadas.
 * Cualquier error (estado distinto de 200, excepción, una respuesta distinta de la que da esa misma consulta sola o <b>un nodo que muestra u oculta un campo
 * contra lo que su empresa configuró</b>) cuenta y deben ser 0.
 *
 * <p>Se mide en dos escenarios con el mismo grafo: <b>privado</b> (dos empresas que no comparten nada: el filtro B2B oculta todo lo ajeno) y <b>con empresas que
 * comparten distintos campos</b> (cuatro empresas dueñas de los lotes por posición; las ajenas comparten, respectivamente, proveedor-cantidad-maquinaria,
 * origen-composición-desperdicio y todo). En ambos, cada nodo de cada respuesta se comprueba contra la configuración de su empresa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "trazatex.messaging.enabled=false")
class LineageConcurrencyPerformanceIT {

    static final int CONCURRENT = 100;
    static final int WARMUP_WAVES = 3;
    static final int MEASURED_WAVES = 10;
    static final long RNF_04_P95_MILLIS = 5_000;

    static final UUID COMPANY_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID COMPANY_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000a0a1");

    /** Una forma de repartir el grafo entre empresas y de qué comparte cada una (los nombres de los campos del JSON). */
    private record Scenario(String label, List<UUID> owners, Map<UUID, Set<String>> shared) {
    }

    static final UUID COMPANY_C = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    static final UUID COMPANY_D = UUID.fromString("00000000-0000-0000-0000-0000000000d4");
    static final List<String> BATCH_CONFIGURABLE = List.of("supplierName", "origin", "quantityKg", "composition", "receptionCharacteristics");
    static final List<String> TRANSFORMATION_CONFIGURABLE = List.of("machineName", "machineType", "shrinkageKg", "wasteKg", "wasteReason");
    /** Campo configurable de la privacidad → campos del JSON que controla. */
    static final Map<String, List<String>> JSON_FIELDS = Map.of("SUPPLIER", List.of("supplierName"), "ORIGIN", List.of("origin"), "QUANTITY", List.of("quantityKg"),
            "COMPOSITION", List.of("composition"), "RECEPTION_CHARACTERISTICS", List.of("receptionCharacteristics"), "MACHINERY", List.of("machineName", "machineType"),
            "SHRINKAGE", List.of("shrinkageKg"), "WASTE", List.of("wasteKg"), "WASTE_REASON", List.of("wasteReason"));

    private static final Scenario PRIVATE = new Scenario("privado (2 empresas, nada compartido)", List.of(COMPANY_A, COMPANY_B), Map.of());
    private static final Scenario SHARING = new Scenario("con empresas que comparten distintos campos (4 empresas)", List.of(COMPANY_A, COMPANY_B, COMPANY_C, COMPANY_D),
            Map.of(COMPANY_B, Set.of("SUPPLIER", "QUANTITY", "MACHINERY"), COMPANY_C, Set.of("ORIGIN", "COMPOSITION", "WASTE"),
                    COMPANY_D, Set.of("SUPPLIER", "ORIGIN", "QUANTITY", "COMPOSITION", "RECEPTION_CHARACTERISTICS", "MACHINERY", "SHRINKAGE", "WASTE", "WASTE_REASON")));

    @DynamicPropertySource
    static void neo4j(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", SharedNeo4j.CONTAINER::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> SharedNeo4j.PASSWORD);
    }

    @LocalServerPort
    int port;
    @Autowired
    Neo4jClient neo4j;
    @Autowired
    ObjectMapper json;

    /** Una consulta: la ruta y los nodos que debe devolver (lo que da esa misma consulta ejecutada sola). */
    private record Query(String name, String path, int nodes, int edges) {
    }

    private HttpClient client;

    @BeforeEach
    void newClient() {
        client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** Carga el diamante con las empresas del escenario y la privacidad de cada una (como la dejaría su último {@code CompanyPrivacyUpdated}). */
    private void load(Scenario scenario) {
        neo4j.query("MATCH (n) DETACH DELETE n").run();
        DiamondGraph.load(neo4j, scenario.owners());
        assertThat(DiamondGraph.nodeCount(neo4j)).as("el grafo de RNF-04").isEqualTo(500L);
        for (UUID owner : scenario.owners()) {
            Map<String, String> privacy = new java.util.LinkedHashMap<>();
            for (String field : List.of("SUPPLIER", "ORIGIN", "QUANTITY", "COMPOSITION", "RECEPTION_CHARACTERISTICS", "MACHINERY", "SHRINKAGE", "WASTE", "WASTE_REASON",
                    "FAILURE_CAUSE", "QUALITY_TEST_VALUES")) {
                privacy.put(field, scenario.shared().getOrDefault(owner, Set.of()).contains(field) ? "SHARED" : "PRIVATE");
            }
            neo4j.query("MERGE (c:Company {companyId: $company}) SET c.privacy = $privacy, c.businessName = $name").bind(owner.toString()).to("company")
                    .bind(json.writeValueAsString(privacy)).to("privacy").bind("Empresa " + owner.toString().substring(31)).to("name").run();
        }
    }

    /**
     * Revisa cada nodo de una respuesta contra la configuración de su empresa: quien consulta es {@code owners[0]}, así que lo suyo sale completo y de las demás
     * solo lo que comparten (un campo oculto no está en el JSON). @return el primer incumplimiento, o nulo
     */
    private static String privacyViolation(JsonNode body, Scenario scenario) {
        for (JsonNode node : body.get("nodes")) {
            UUID company = UUID.fromString(node.get("companyId").asString());
            boolean own = company.equals(scenario.owners().getFirst());
            boolean batch = node.get("kind").asString().equals("BATCH");
            for (String field : batch ? BATCH_CONFIGURABLE : TRANSFORMATION_CONFIGURABLE) {
                boolean shown = own || scenario.shared().getOrDefault(company, Set.of()).stream().anyMatch(shared -> JSON_FIELDS.getOrDefault(shared, List.of()).contains(field));
                if (node.has(field) != shown) {
                    return (shown ? "falta " : "sobra ") + field + " en " + node.get("kind").asString() + " " + node.get("id").asString() + " de la empresa " + company
                            + (own ? " (propia)" : " (ajena)");
                }
            }
            if (!own && node.has("qualityHold")) {
                return "sobra qualityHold en un lote ajeno " + node.get("id").asString();
            }
        }
        return null;
    }

    private static String route(String direction, int layer, int position) {
        return "/api/traceability/batches/" + DiamondGraph.batchId(layer, position) + "/" + direction;
    }

    /** 36 consultas distintas: ancestros desde las capas altas, descendientes desde las bajas y la completa desde las del medio, con distintas posiciones. */
    private List<Query> queries() {
        List<Query> list = new ArrayList<>();
        for (int position : new int[]{2, 9, 14, 21}) {
            for (int layer : new int[]{10, 8, 6}) {
                list.add(new Query("ancestors L" + layer + "/" + position, route("ancestors", layer, position), -1, -1));
            }
            for (int layer : new int[]{0, 2, 4}) {
                list.add(new Query("descendants L" + layer + "/" + position, route("descendants", layer, position), -1, -1));
            }
            for (int layer : new int[]{3, 5, 7}) {
                list.add(new Query("lineage L" + layer + "/" + position, route("lineage", layer, position), -1, -1));
            }
        }
        return list;
    }

    private HttpRequest request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("X-User-Id", USER.toString()).header("X-Company-Id", COMPANY_A.toString())
                .header("X-User-Role", "OPERATOR").timeout(Duration.ofSeconds(60)).GET().build();
    }

    /** Cada consulta sola, antes de la carga: lo que debe devolver también bajo carga. */
    private List<Query> baseline(Scenario scenario) throws Exception {
        List<Query> expected = new ArrayList<>();
        for (Query query : queries()) {
            HttpResponse<String> response = client.send(request(query.path()), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(query.name()).isEqualTo(200);
            JsonNode body = json.readTree(response.body());
            assertThat(body.get("complete").asBoolean()).as("%s completa", query.name()).isTrue();
            assertThat(body.get("truncated").asBoolean()).as("%s sin cortar", query.name()).isFalse();
            assertThat(privacyViolation(body, scenario)).as("%s sola, sin carga: la privacidad de cada empresa", query.name()).isNull();
            expected.add(new Query(query.name(), query.path(), body.get("nodes").size(), body.get("edges").size()));
        }
        return expected;
    }

    private record Sample(long nanos, boolean ok, String problem) {
    }

    /** Una oleada: 100 peticiones que salen en el mismo instante. */
    private List<Sample> wave(List<Query> queries, int waveNumber, ExecutorService pool, Scenario scenario) throws Exception {
        CountDownLatch ready = new CountDownLatch(CONCURRENT);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Sample>> futures = new ArrayList<>();
        for (int k = 0; k < CONCURRENT; k++) {
            Query query = queries.get((k + waveNumber * 7) % queries.size());
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                long start = System.nanoTime();
                try {
                    HttpResponse<String> response = client.send(request(query.path()), HttpResponse.BodyHandlers.ofString());
                    long elapsed = System.nanoTime() - start;
                    if (response.statusCode() != 200) {
                        return new Sample(elapsed, false, query.name() + ": estado " + response.statusCode() + " " + abbreviate(response.body()));
                    }
                    JsonNode body = json.readTree(response.body());
                    if (body.get("nodes").size() != query.nodes() || body.get("edges").size() != query.edges() || !body.get("complete").asBoolean()
                            || body.get("truncated").asBoolean()) {
                        return new Sample(elapsed, false, query.name() + ": respuesta distinta de la de la consulta sola (nodos " + body.get("nodes").size() + " de "
                                + query.nodes() + ", aristas " + body.get("edges").size() + " de " + query.edges() + ")");
                    }
                    String violation = privacyViolation(body, scenario);
                    if (violation != null) {
                        return new Sample(elapsed, false, query.name() + ": privacidad bajo carga: " + violation);
                    }
                    return new Sample(elapsed, true, null);
                } catch (Exception e) {
                    return new Sample(System.nanoTime() - start, false, query.name() + ": " + e);
                }
            }));
        }
        assertThat(ready.await(30, TimeUnit.SECONDS)).as("los 100 hilos listos").isTrue();
        go.countDown();
        List<Sample> samples = new ArrayList<>();
        for (Future<Sample> future : futures) {
            samples.add(future.get(120, TimeUnit.SECONDS));
        }
        return samples;
    }

    private static String abbreviate(String text) {
        return text.length() > 160 ? text.substring(0, 160) + "…" : text;
    }

    /** Percentil por rango más cercano sobre una lista ordenada (milisegundos). */
    private static double percentile(List<Long> sortedNanos, double p) {
        int index = (int) Math.ceil(p / 100.0 * sortedNanos.size()) - 1;
        return sortedNanos.get(Math.max(0, Math.min(index, sortedNanos.size() - 1))) / 1_000_000.0;
    }

    @Test
    void oneHundredSimultaneousQueriesOverHttpAnswerWithP95UnderFiveSecondsAndNoErrors() throws Exception {
        measure(PRIVATE);
    }

    @Test
    void theSameWithTheB2BFilterActiveAndCompaniesSharingDifferentFields() throws Exception {
        measure(SHARING);
    }

    private void measure(Scenario scenario) throws Exception {
        load(scenario);
        List<Query> queries = baseline(scenario);
        AtomicInteger names = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT, runnable -> new Thread(runnable, "rnf04-client-" + names.incrementAndGet()));
        try {
            for (int w = 0; w < WARMUP_WAVES; w++) {
                List<Sample> warm = wave(queries, w, pool, scenario);
                assertThat(warm).as("calentamiento %d", w).allMatch(Sample::ok);
            }
            List<Long> all = new ArrayList<>();
            List<String> problems = new ArrayList<>();
            List<Double> waveP95 = new ArrayList<>();
            List<Long> waveMillis = new ArrayList<>();
            for (int w = 0; w < MEASURED_WAVES; w++) {
                long start = System.nanoTime();
                List<Sample> samples = wave(queries, WARMUP_WAVES + w, pool, scenario);
                waveMillis.add((System.nanoTime() - start) / 1_000_000);
                List<Long> nanos = new ArrayList<>();
                for (Sample sample : samples) {
                    nanos.add(sample.nanos());
                    if (!sample.ok()) {
                        problems.add(sample.problem());
                    }
                }
                Collections.sort(nanos);
                waveP95.add(percentile(nanos, 95));
                all.addAll(nanos);
            }
            Collections.sort(all);
            double p50 = percentile(all, 50);
            double p95 = percentile(all, 95);
            double p99 = percentile(all, 99);
            double max = all.get(all.size() - 1) / 1_000_000.0;
            System.out.printf("RNF-04 [" + scenario.label() + "] %d consultas simultáneas por oleada × %d oleadas medidas (%d peticiones, tras %d de calentamiento), grafo de 500 nodos, %d consultas distintas, "
                            + "HTTP directo a traceability: p50=%.1f ms p95=%.1f ms p99=%.1f ms máximo=%.1f ms errores=%d | p95 por oleada: peor=%.1f ms mejor=%.1f ms | "
                            + "duración de una oleada: mediana=%d ms máxima=%d ms (límite RNF-04: p95 ≤ %d ms)%n",
                    CONCURRENT, MEASURED_WAVES, all.size(), WARMUP_WAVES, queries.size(), p50, p95, p99, max, problems.size(), Collections.max(waveP95), Collections.min(waveP95),
                    waveMillis.stream().sorted().toList().get(waveMillis.size() / 2), Collections.max(waveMillis), RNF_04_P95_MILLIS);

            assertThat(problems).as("errores (deben ser 0)").isEmpty();
            assertThat(all).hasSize(CONCURRENT * MEASURED_WAVES);
            assertThat(p95).as("RNF-04: p95 ≤ 5 s con 100 consultas simultáneas").isLessThanOrEqualTo((double) RNF_04_P95_MILLIS);
        } finally {
            pool.shutdownNow();
        }
    }
}
