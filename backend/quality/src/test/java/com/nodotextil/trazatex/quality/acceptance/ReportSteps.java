package com.nodotextil.trazatex.quality.acceptance;

import com.nodotextil.trazatex.quality.integration.PdfSwitch;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** US44: el reporte PDF de un control finalizado, generado al vuelo y sin almacenarse; y qué pasa si el generador no responde. */
public class ReportSteps extends StepSupport {

    public ReportSteps(World world, QualityApi api, JdbcClient jdbc, ObjectMapper json) {
        super(world, api, jdbc, json);
    }

    private Reply requestReport(UUID control) {
        return api.get(world.adminA, "/api/quality/controls/" + control + "/report");
    }

    private static String textOf(byte[] pdf) {
        try (PDDocument document = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            throw new IllegalStateException("el reporte no es un PDF legible", e);
        }
    }

    // ---- esc. 1: generación exitosa ----

    @Dado("^que existe un control de calidad finalizado$")
    public void aFinishedQualityControlExists() {
        UUID control = finishedControl();
        world.tablesBefore = tableCounts();
        world.pending = () -> requestReport(control);
    }

    @Cuando("^el administrador solicita generar su reporte$")
    public void theAdministratorAsksForItsReport() {
        world.submit();
    }

    @Entonces("^el sistema genera el documento con las pruebas, valores y resultados registrados$")
    public void theSystemGeneratesTheDocumentWithTheTestsValuesAndResults() {
        assertThat(world.last.status()).as("generar el reporte: %s", world.last.raw()).isEqualTo(200);
        assertThat(world.last.header("content-type")).isEqualTo("application/pdf");
        assertThat(new String(world.last.bytes(), 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(textOf(world.last.bytes())).contains(world.control.toString())
                .contains("Humedad", "medición de planta")          // la prueba y su observación
                .contains("8").contains("7").contains("15").contains("%")   // el esperado, los obtenidos y la unidad
                .contains("Conforme").contains("Con falla");        // el resultado de cada prueba y el general
    }

    @Y("^no lo almacena dentro del sistema\\.$")
    public void doesNotStoreItInsideTheSystem() {
        Map<String, Long> after = tableCounts();
        Map<String, Long> expected = new java.util.TreeMap<>(world.tablesBefore);
        expected.merge("audit_log", 1L, Long::sum);          // lo único que queda: quién lo pidió, no el contenido
        assertThat(after).as("ninguna tabla guarda el documento; solo la auditoría de que se generó").isEqualTo(expected);
        assertThat(world.last.header("cache-control")).isEqualTo("no-store");
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'REPORT_GENERATED' AND entity_id = ?", world.control.toString())).isEqualTo(1);
    }

    // ---- esc. 2: el generador no responde ----

    @Dado("^que el servicio de generación de PDF no responde$")
    public void thePdfGenerationServiceDoesNotRespond() {
        UUID control = finishedControl();
        PdfSwitch.stop();
        world.pending = () -> requestReport(control);
    }

    @Cuando("^el administrador intenta generar el reporte$")
    public void theAdministratorTriesToGenerateTheReport() {
        world.submit();
    }

    @Entonces("^el sistema comunica el error$")
    public void theSystemReportsTheError() {
        assertThat(world.last.status()).as("generar el reporte con el generador caído: %s", world.last.raw()).isEqualTo(503);
        assertThat(world.last.code()).isEqualTo("REPORT_UNAVAILABLE");
        assertThat(world.last.message()).contains("Intenta de nuevo");
        assertThat(world.last.header("cache-control")).isEqualTo("no-store");
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'REPORT_GENERATED'")).as("un reporte que no se generó no deja auditoría").isZero();
    }

    @Y("^permite reintentar\\.$")
    public void allowsRetrying() {
        assertThat(world.last.header("retry-after")).isEqualTo("5");
        PdfSwitch.start();          // el generador vuelve
        Reply retried = world.submit();
        assertThat(retried.status()).as("reintentar el reporte: %s", retried.raw()).isEqualTo(200);
        assertThat(new String(retried.bytes(), 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'REPORT_GENERATED'")).isEqualTo(1);
    }
}
