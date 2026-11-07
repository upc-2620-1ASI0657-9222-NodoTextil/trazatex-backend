package com.nodotextil.trazatex.quality.infrastructure.external;

import com.nodotextil.trazatex.quality.application.dto.ControlReport;
import com.nodotextil.trazatex.quality.application.exception.PdfGenerationException;
import com.nodotextil.trazatex.quality.application.port.out.PdfGeneratorPort;
import com.nodotextil.trazatex.quality.domain.model.ControlType;
import com.nodotextil.trazatex.quality.domain.model.TestResult;
import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.extend.FSStreamFactory;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ADR-011: el PDF se genera localmente con Thymeleaf (plantilla {@code templates/control-report.html}, valores siempre escapados) y OpenHTMLtoPDF.
 *
 * <p><b>El generador no puede cargar nada de afuera.</b> Ni imágenes, ni hojas de estilo, ni fuentes: ningún URI se resuelve y los protocolos de red
 * ({@code http}, {@code https}, {@code ftp}, {@code file}, {@code jar}) están bloqueados con una implementación que no abre nada y cuenta el intento.
 * Tampoco se admite {@code DOCTYPE} ni entidades en lo que se renderiza (XXE / SSRF). El texto del usuario además va escapado por Thymeleaf, así que
 * un criterio con {@code <img src="http://...">} sale como texto.
 *
 * <p>Protección del servicio: un tiempo máximo por generación y un tope de generaciones simultáneas; superarlos es un {@link PdfGenerationException}
 * (503 {@code REPORT_UNAVAILABLE}, reintentable). Los textos se limitan a lo que representan las fuentes base del PDF (Windows-1252); lo demás sale como {@code ?}.
 */
public class OpenHtmlToPdfAdapter implements PdfGeneratorPort {

    private static final Logger log = LoggerFactory.getLogger(OpenHtmlToPdfAdapter.class);

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "PE"));
    private static final String[] NETWORK_PROTOCOLS = {"http", "https", "ftp", "file", "jar"};
    private static final String LOGO = logoData();
    private static String logoData() {
        try (var stream = OpenHtmlToPdfAdapter.class.getResourceAsStream("/brand/wordmark.png")) {
            return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(java.util.Objects.requireNonNull(stream).readAllBytes());
        } catch (java.io.IOException error) { throw new IllegalStateException("No se pudo leer el logo del informe.", error); }
    }
    private static final Charset WIN_1252 = Charset.forName("windows-1252");

    /** Lo que «se carga» de un recurso bloqueado: nada. */
    private static final FSStream EMPTY = new FSStream() {
        @Override
        public java.io.InputStream getStream() {
            return new java.io.ByteArrayInputStream(new byte[0]);
        }

        @Override
        public java.io.Reader getReader() {
            return new java.io.StringReader("");
        }
    };

    private final TemplateEngine templates;
    private final Duration timeout;
    private final int maxConcurrent;
    private final Semaphore permits;
    private final ExecutorService executor;
    private final AtomicInteger blockedFetches = new AtomicInteger();

    public OpenHtmlToPdfAdapter(Duration timeout, int maxConcurrent) {
        this.timeout = timeout;
        this.maxConcurrent = maxConcurrent;
        this.permits = new Semaphore(maxConcurrent);
        this.executor = new ThreadPoolExecutor(maxConcurrent, maxConcurrent, 30, TimeUnit.SECONDS, new java.util.concurrent.SynchronousQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "pdf-report");
            thread.setDaemon(true);
            return thread;
        });
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        this.templates = new TemplateEngine();
        this.templates.setTemplateResolver(resolver);
    }

    @Override
    public byte[] render(ControlReport report) {
        String xhtml;
        try {
            xhtml = templates.process("control-report", context(report));
        } catch (RuntimeException e) {
            throw new PdfGenerationException("No se pudo armar el reporte", e);
        }
        return renderWithinLimits(xhtml);
    }

    /** Cuántas generaciones hay en curso (para las pruebas). */
    int inFlight() {
        return maxConcurrent - permits.availablePermits();
    }

    /** Los intentos de cargar algo de afuera que se bloquearon (para las pruebas). */
    int blockedFetches() {
        return blockedFetches.get();
    }

    /** Renderiza un XHTML ya armado, con el tiempo y la concurrencia acotados. Package-private: las pruebas le pasan marcado hostil. */
    byte[] renderWithinLimits(String xhtml) {
        if (!permits.tryAcquire()) {
            throw new PdfGenerationException("Hay demasiados reportes generándose a la vez");
        }
        Future<byte[]> future;
        try {
            future = executor.submit(() -> {
                try {
                    return renderHtml(xhtml);
                } finally {
                    permits.release();
                }
            });
        } catch (RuntimeException rejected) {
            permits.release();
            throw new PdfGenerationException("No se pudo iniciar la generación del reporte", rejected);
        }
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new PdfGenerationException("La generación del reporte superó " + timeout, e);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof PdfGenerationException known ? known : new PdfGenerationException("Falló la generación del reporte", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new PdfGenerationException("Se interrumpió la generación del reporte", e);
        }
    }

    private byte[] renderHtml(String xhtml) {
        String probe = xhtml.toUpperCase(Locale.ROOT);
        if (probe.contains("<!DOCTYPE") || probe.contains("<!ENTITY")) {
            throw new PdfGenerationException("El marcado del reporte no admite DOCTYPE ni entidades");
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(() -> getClass().getResourceAsStream("/fonts/arvo-regular.ttf"), "Arvo", 400, com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.useFont(() -> getClass().getResourceAsStream("/fonts/arvo-bold.ttf"), "Arvo", 700, com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.withHtmlContent(xhtml, null);
            builder.useUriResolver((base, uri) -> {
                if (LOGO.equals(uri)) return uri;
                blockedFetches.incrementAndGet();
                log.warn("El reporte intentó cargar un recurso externo ({}): se ignora", uri);
                return null;
            });
            FSStreamFactory blocked = url -> {
                blockedFetches.incrementAndGet();
                log.warn("El reporte intentó abrir {}: bloqueado", url);
                return EMPTY;
            };
            builder.useHttpStreamImplementation(blocked);
            builder.useProtocolsStreamImplementation(blocked, NETWORK_PROTOCOLS);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (PdfGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new PdfGenerationException("Falló la generación del reporte", e);
        }
    }

    // ---- el contexto de la plantilla: todo texto, saneado ----

    private Context context(ControlReport report) {
        Context context = new Context(new Locale("es", "PE"));
        context.setVariable("logo", LOGO);
        context.setVariable("controlId", report.controlId().toString());
        context.setVariable("batch", clean(report.batchReference()));
        context.setVariable("type", report.controlType() == ControlType.EVALUATION ? "Evaluación" : "Reevaluación");
        context.setVariable("startedAt", format(report.startedAt()));
        context.setVariable("finishedAt", format(report.finishedAt()));
        context.setVariable("result", label(report.result()));
        context.setVariable("resultClass", css(report.result()));
        context.setVariable("generatedAt", format(report.generatedAt()));
        List<Map<String, String>> tests = report.tests().stream().map(t -> Map.of(
                "criterion", clean(t.criterion()), "expected", clean(t.expectedValue()), "obtained", clean(t.obtainedValue()), "unit", clean(t.unit()),
                "result", label(t.result()), "resultClass", css(t.result()), "remarks", t.remarks() == null ? "" : clean(t.remarks()))).toList();
        context.setVariable("tests", tests);
        return context;
    }

    private static String label(TestResult result) {
        return result == TestResult.CONFORMING ? "Conforme" : "Con falla";
    }

    private static String css(TestResult result) {
        return result == TestResult.CONFORMING ? "conforming" : "failed";
    }

    private static String format(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant.atZone(LIMA));
    }

    /** Solo lo que las fuentes base del PDF pueden dibujar (Windows-1252); el resto, y los caracteres de control, salen como «?». */
    static String clean(String text) {
        if (text == null) {
            return "";
        }
        CharsetEncoder encoder = WIN_1252.newEncoder();
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            boolean control = Character.isISOControl(cp) && cp != '\n' && cp != '\t';
            if (control || !encoder.canEncode(new String(Character.toChars(cp)))) {
                out.append('?');
            } else {
                out.appendCodePoint(cp);
            }
        });
        return out.toString();
    }
}
