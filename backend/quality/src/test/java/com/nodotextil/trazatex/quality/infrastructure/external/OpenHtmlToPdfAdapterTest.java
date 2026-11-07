package com.nodotextil.trazatex.quality.infrastructure.external;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nodotextil.trazatex.quality.application.dto.ControlReport;
import com.nodotextil.trazatex.quality.application.exception.PdfGenerationException;
import com.nodotextil.trazatex.quality.domain.model.ControlType;
import com.nodotextil.trazatex.quality.domain.model.TestResult;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/** El generador de PDF real (Thymeleaf + OpenHTMLtoPDF): su contenido, el escape, que NO carga nada de afuera, el tiempo máximo y el tope de generaciones. */
class OpenHtmlToPdfAdapterTest {

    static WireMockServer outside;
    final OpenHtmlToPdfAdapter adapter = new OpenHtmlToPdfAdapter(Duration.ofSeconds(20), 4);

    @BeforeAll
    static void startOutside() {
        outside = new WireMockServer(wireMockConfig().dynamicPort());
        outside.start();
    }

    @AfterAll
    static void stopOutside() {
        outside.stop();
    }

    @BeforeEach
    void reset() {
        outside.resetAll();
        outside.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200).withBody("x")));
    }

    static ControlReport report(ControlReport.Test... tests) {
        return new ControlReport(UUID.fromString("11111111-2222-3333-4444-555555555555"), "TT-ABCDEFGHJK", ControlType.EVALUATION,
                Instant.parse("2026-10-23T12:00:00Z"), Instant.parse("2026-10-23T13:30:00Z"), TestResult.FAILED, List.of(tests), Instant.parse("2026-10-24T15:05:00Z"));
    }

    static ControlReport.Test test(String criterion, String remarks) {
        return new ControlReport.Test(criterion, "50", "52,3", "N", TestResult.CONFORMING, remarks);
    }

    static String text(byte[] pdf) throws IOException {
        try (PDDocument document = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    String rendered(ControlReport report) throws IOException {
        byte[] pdf = adapter.render(report);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        return text(pdf);
    }

    @Test
    void embedsBrandFontAndCompleteLogo() throws IOException {
        try (PDDocument document = PDDocument.load(adapter.render(report(test("Gramaje", "Muestra conforme"))))) {
            var resources = document.getPage(0).getResources();
            int embedded = 0;
            for (var name : resources.getFontNames()) {
                var font = resources.getFont(name);
                if (font.getName().contains("Arvo") && font.isEmbedded()) embedded++;
            }
            assertThat(embedded).isGreaterThanOrEqualTo(2);
            assertThat(resources.getXObjectNames().iterator().hasNext()).isTrue();
            if (Boolean.getBoolean("trazatex.pdf.preview")) {
                javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(document).renderImageWithDPI(0, 100), "png", new java.io.File("target/report-preview.png"));
            }
        }
    }

    // ---- contenido ----

    @Test
    void theReportCarriesTheControlTheBatchTheDatesInLimaAndEveryTestWithItsRemarks() throws IOException {
        String content = rendered(report(test("Resistencia a la tracción", "dentro del rango"),
                new ControlReport.Test("Humedad", "8", "15", "%", TestResult.FAILED, null)));

        assertThat(content).contains("Reporte de control de calidad").contains("11111111-2222-3333-4444-555555555555").contains("TT-ABCDEFGHJK")
                .contains("Evaluación").contains("Con falla")
                .contains("23/10/2026 07:00").contains("23/10/2026 08:30")      // UTC-5
                .contains("24/10/2026 10:05")
                .contains("Resistencia a la tracción").contains("52,3").contains("dentro del rango")
                .contains("Humedad").contains("Conforme").contains("no se almacena");
    }

    @Test
    void aReevaluationIsLabelledAsSuch() throws IOException {
        ControlReport base = report(test("Humedad", null));

        String content = rendered(new ControlReport(base.controlId(), base.batchReference(), ControlType.REEVALUATION, base.startedAt(), base.finishedAt(),
                TestResult.CONFORMING, base.tests(), base.generatedAt()));

        assertThat(content).contains("Reevaluación").contains("Conforme");
    }

    @Test
    void textTheFontsCannotDrawBecomesAQuestionMarkAndNeverBreaksTheReport() throws IOException {
        String content = rendered(report(test("Resistencia ≥ 95 µm 😀 ñandú", "cuidado\u0007")));

        assertThat(content).contains("Resistencia ? 95 µm ? ñandú").contains("cuidado?");
        assertThat(OpenHtmlToPdfAdapter.clean(null)).isEmpty();
    }

    // ---- lo que escribe el usuario nunca es marcado ----

    @Test
    void whatTheUserWritesIsTextEvenIfItLooksLikeMarkupAndNothingIsRequestedFromOutside() throws IOException {
        String hostile = "<img src=\"" + outside.baseUrl() + "/espia.png\"/> <script>alert(1)</script> <a href=\"" + outside.baseUrl() + "\">clic</a>";

        String content = rendered(report(test(hostile, "<link rel=\"stylesheet\" href=\"" + outside.baseUrl() + "/x.css\"/> &amp; fin")));

        assertThat(content.replaceAll("\\s+", "")).contains("<script>alert(1)</script>").contains("<imgsrc=\"http://localhost:").contains("espia.").contains("&amp;fin");
        assertThat(outside.getAllServeEvents()).as("ninguna petición a la red").isEmpty();
        assertThat(adapter.blockedFetches()).isZero();
    }

    // ---- el generador no puede cargar recursos externos ----

    private static String xhtml(String head, String body) {
        return "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>t</title>" + head + "</head><body>" + body + "</body></html>";
    }

    @Test
    void evenIfHostileMarkupReachedTheRendererNoExternalResourceIsLoaded() {
        String url = outside.baseUrl();
        List<String> documents = List.of(
                xhtml("", "<p>hola</p><img src=\"" + url + "/imagen.png\"/>"),
                xhtml("<link rel=\"stylesheet\" type=\"text/css\" href=\"" + url + "/estilo.css\"/>", "<p>hola</p>"),
                xhtml("<style>@import url(" + url + "/importado.css);</style>", "<p>hola</p>"),
                xhtml("<style>body { background-image: url(" + url + "/fondo.png); }</style>", "<p>hola</p>"),
                xhtml("<style>@font-face { font-family: 'X'; src: url(" + url + "/fuente.ttf); } p { font-family: 'X'; }</style>", "<p>hola</p>"),
                xhtml("", "<p style=\"background: url('" + url + "/inline.png')\">hola</p>"),
                xhtml("", "<img src=\"file:///etc/hosts\"/><img src=\"//localhost:" + outside.port() + "/relativo-al-protocolo.png\"/>"),
                xhtml("", "<object data=\"" + url + "/objeto\"></object><iframe src=\"" + url + "/marco\"></iframe>"));

        for (String document : documents) {
            try {
                byte[] pdf = adapter.renderWithinLimits(document);
                assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
            } catch (PdfGenerationException tolerated) {
                // rechazarlo también es válido: lo que no puede pasar es salir a la red
            }
        }

        assertThat(outside.getAllServeEvents()).as("ni una sola petición de red: %s", outside.getAllServeEvents()).isEmpty();
        assertThat(adapter.blockedFetches()).as("sí intentó cargarlos y se bloquearon").isPositive();
    }

    @Test
    void doctypeAndExternalEntitiesAreRefusedBeforeParsingSoNothingIsFetched() {
        String xxe = "<!DOCTYPE html [ <!ENTITY secreto SYSTEM \"" + outside.baseUrl() + "/xxe\"> ]><html xmlns=\"http://www.w3.org/1999/xhtml\"><body>&secreto;</body></html>";
        String dtd = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"" + outside.baseUrl() + "/dtd\"><html xmlns=\"http://www.w3.org/1999/xhtml\"><body>x</body></html>";

        assertThatThrownBy(() -> adapter.renderWithinLimits(xxe)).isInstanceOf(PdfGenerationException.class);
        assertThatThrownBy(() -> adapter.renderWithinLimits(dtd)).isInstanceOf(PdfGenerationException.class);

        assertThat(outside.getAllServeEvents()).isEmpty();
    }

    // ---- límites ----

    @Test
    void aGenerationThatTakesTooLongFailsAndCanBeRetried() throws IOException {
        OpenHtmlToPdfAdapter impatient = new OpenHtmlToPdfAdapter(Duration.ofMillis(1), 2);

        assertThatThrownBy(() -> impatient.render(report(test("Humedad", null)))).isInstanceOf(PdfGenerationException.class).hasMessageContaining("superó");

        assertThat(rendered(report(test("Humedad", null)))).as("el generador normal sigue funcionando").contains("Humedad");
    }

    @Test
    void tooManyGenerationsAtTheSameTimeAreRefusedInsteadOfPilingUp() {
        OpenHtmlToPdfAdapter single = new OpenHtmlToPdfAdapter(Duration.ofSeconds(60), 1);
        List<ControlReport.Test> many = new ArrayList<>();
        for (int i = 0; i < 4000; i++) {
            many.add(test("Criterio " + i, "observación " + i));
        }
        CompletableFuture<byte[]> first = CompletableFuture.supplyAsync(() -> single.render(report(many.toArray(new ControlReport.Test[0]))));
        await().atMost(10, TimeUnit.SECONDS).until(() -> single.inFlight() == 1);

        assertThatThrownBy(() -> single.render(report(test("Humedad", null)))).isInstanceOf(PdfGenerationException.class).hasMessageContaining("demasiados");

        assertThat(first.orTimeout(60, TimeUnit.SECONDS).join()).isNotEmpty();
        assertThat(single.inFlight()).isZero();
    }
}
