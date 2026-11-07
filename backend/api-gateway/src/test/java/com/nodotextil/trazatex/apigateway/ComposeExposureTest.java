package com.nodotextil.trazatex.apigateway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * M8: el compose confía por defecto en {@code X-Forwarded-For} para el límite por IP ({@code TRAZATEX_RATE_LIMIT_TRUST_FORWARDED_FOR=true}).
 * Eso solo es seguro si <b>el gateway no se puede alcanzar directamente</b>: si se publicara en el host, cualquiera enviaría un
 * {@code X-Forwarded-For} inventado y saltaría el límite. Esta prueba lee {@code compose.yaml} y falla si el servicio {@code gateway}
 * publica un puerto, expone uno, usa la red del host o hereda configuración de otro archivo ({@code extends}, que podría traer un {@code ports}).
 *
 * <p>Se omite (no pasa en falso: queda como «skipped») si el backend se ejecuta sin el {@code compose.yaml} de la plataforma al lado.
 */
class ComposeExposureTest {

    private static Path compose() {
        Path module = Path.of("").toAbsolutePath();
        for (Path dir = module; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("compose.yaml");
            if (Files.isRegularFile(candidate) && !dir.equals(module)) return candidate;
        }
        return null;
    }

    /** Las líneas del bloque de un servicio (sus claves están a 2 espacios de sangría), sin comentarios. */
    static List<String> serviceBlock(List<String> lines, String service) {
        List<String> block = new ArrayList<>();
        boolean inside = false;
        for (String line : lines) {
            if (line.matches("^ {2}[A-Za-z0-9_-]+:\\s*$") || line.matches("^\\S.*")) {
                inside = line.strip().equals(service + ":") && line.startsWith("  ") && !line.startsWith("   ");
                continue;
            }
            if (inside && !line.strip().startsWith("#")) block.add(line);
        }
        return block;
    }

    @Test
    void theGatewayIsNotPublishedOnTheHostSoTrustingForwardedForIsSafe() throws IOException {
        Path file = compose();
        assumeTrue(file != null, "no hay compose.yaml de la plataforma junto al backend");
        List<String> lines = Files.readAllLines(file);

        List<String> gateway = serviceBlock(lines, "gateway");

        assertThat(gateway).as("el servicio gateway existe en compose.yaml").isNotEmpty();
        assertThat(gateway).as("el gateway no publica puertos en el host")
                .noneMatch(line -> line.matches("^\\s+ports:.*") || line.matches("^\\s+expose:.*"));
        assertThat(gateway).as("el gateway no usa la red del host").noneMatch(line -> line.contains("network_mode"));
        assertThat(gateway).as("el gateway no hereda de otro archivo (podría traer un ports)").noneMatch(line -> line.matches("^\\s+extends:.*"));
    }

    @Test
    void theAnchorTheGatewayInheritsFromPublishesNothingEither() throws IOException {
        Path file = compose();
        assumeTrue(file != null, "no hay compose.yaml de la plataforma junto al backend");
        List<String> lines = Files.readAllLines(file);
        List<String> anchor = new ArrayList<>();
        boolean inside = false;
        for (String line : lines) {
            if (line.startsWith("x-backend:")) { inside = true; continue; }
            if (inside && (line.isBlank() || !line.startsWith(" "))) break;
            if (inside) anchor.add(line);
        }

        assertThat(anchor).as("x-backend (la plantilla del gateway) existe").isNotEmpty();
        assertThat(anchor).noneMatch(line -> line.matches("^\\s+(ports|expose):.*"));
    }

    @Test
    void onlyTheWebProxyIsInFrontOfTheGateway() throws IOException {
        Path file = compose();
        assumeTrue(file != null, "no hay compose.yaml de la plataforma junto al backend");
        List<String> lines = Files.readAllLines(file);

        // la única forma de llegar al gateway es el Nginx de `web`: el servicio web publica 80 en 127.0.0.1 y depende del gateway
        List<String> web = serviceBlock(lines, "web");
        assertThat(web).anyMatch(line -> line.contains("ports:") && line.contains("127.0.0.1:"));
        assertThat(web).anyMatch(line -> line.contains("depends_on") && line.contains("gateway"));
    }
}
