package com.nodotextil.trazatex.production.acceptance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los escenarios ejecutables (RNF-10) son el texto exacto de {@code docs/domain/user-stories.md}: título y cada paso. Si alguien edita un
 * {@code .feature} a mano, cambia la historia sin regenerar, o falta algún escenario de US10, US12–US22 o US42, esta prueba falla. Las
 * pruebas de abajo comprueban la prueba: aplican cada tipo de cambio a una copia y exigen que se note.
 */
class FeaturesMatchUserStoriesTest {

    private static final Path DOCS = Path.of("..", "docs", "domain", "user-stories.md");
    private static final Path FEATURES = Path.of("src", "test", "resources", "features");

    @TempDir
    Path temp;

    private Path featuresCopy() throws IOException {
        Path copy = Files.createTempDirectory(temp, "features");
        try (Stream<Path> files = Files.list(FEATURES)) {
            for (Path file : files.toList()) {
                Files.copy(file, copy.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return copy;
    }

    private Path feature(Path dir, String id) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(path -> path.getFileName().toString().startsWith(id.toLowerCase() + "-")).findFirst().orElseThrow();
        }
    }

    private List<String> differencesAfter(String id, UnaryOperator<String> edit) throws IOException {
        Path copy = featuresCopy();
        Path file = feature(copy, id);
        String edited = edit.apply(Files.readString(file));
        assertThat(edited).as("la edición de la prueba debe cambiar algo").isNotEqualTo(Files.readString(file));
        Files.writeString(file, edited);
        return UserStoriesComparator.differences(DOCS, copy);
    }

    // ---- lo real ----

    @Test
    void theFeaturesAreExactlyTheScenariosOfUs10Us12ToUs22AndUs42() throws IOException {
        assertThat(UserStoriesComparator.differences(DOCS, FEATURES)).isEmpty();
    }

    @Test
    void thereAreThirteenStoriesAndThirtyScenariosAndNothingElse() throws IOException {
        assertThat(UserStoriesComparator.STORIES).containsExactly("US10", "US12", "US13", "US14", "US15", "US16", "US17", "US18", "US19",
                "US20", "US21", "US22", "US42");
        try (Stream<Path> files = Files.list(FEATURES)) {
            List<Path> list = files.toList();
            assertThat(list).hasSize(13);
            long scenarios = 0;
            for (Path file : list) {
                scenarios += Files.readAllLines(file).stream().filter(line -> line.trim().startsWith("Escenario:")).count();
            }
            assertThat(scenarios).isEqualTo(30);
        }
    }

    // ---- la prueba de la prueba ----

    @Test
    void aMissingScenarioIsNoticed() throws IOException {
        assertThat(differencesAfter("US20", text -> text.substring(0, text.indexOf("  Escenario: 4:")).stripTrailing() + "\n"))
                .anyMatch(d -> d.startsWith("US20: falta el Escenario 4:"));
    }

    @Test
    void anExtraScenarioIsNoticed() throws IOException {
        assertThat(differencesAfter("US14", text -> text + "\n  Escenario: 3: Uno de más\n    Dado que algo\n    Cuando ocurre\n    Entonces pasa.\n"))
                .anyMatch(d -> d.startsWith("US14: sobra el escenario"));
    }

    @Test
    void aSingleChangedWordInAStepIsNoticed() throws IOException {
        assertThat(differencesAfter("US21", text -> text.replace("alerta a los administradores receptores", "avisa a los administradores receptores")))
                .anyMatch(d -> d.startsWith("US21 Escenario 1, paso 6: esperaba «Y alerta a los administradores receptores.»"));
    }

    @Test
    void aChangedScenarioTitleIsNoticed() throws IOException {
        assertThat(differencesAfter("US12", text -> text.replace("Proveedor desconocido", "Proveedor ignorado")))
                .anyMatch(d -> d.startsWith("US12 Escenario 2: el título difiere"));
    }

    @Test
    void aMissingAndAnExtraStepAreNoticed() throws IOException {
        assertThat(differencesAfter("US22", text -> text.replace("    Y conserva sus Traceability ID y QR.\n", "")))
                .anyMatch(d -> d.startsWith("US22 Escenario 1: falta el paso 6"));
        assertThat(differencesAfter("US22", text -> text.replace("    Y conserva sus Traceability ID y QR.\n", "    Y conserva sus Traceability ID y QR.\n    Y hace algo más.\n")))
                .anyMatch(d -> d.startsWith("US22 Escenario 1: sobra el paso 7"));
    }

    @Test
    void reorderedStepsAndAChangedKeywordAreNoticed() throws IOException {
        assertThat(differencesAfter("US15", text -> text.replace("    Cuando confirma la división\n    Entonces el lote padre cambia a Dividido\n",
                "    Entonces el lote padre cambia a Dividido\n    Cuando confirma la división\n"))).isNotEmpty();
        assertThat(differencesAfter("US15", text -> text.replace("    Cuando intenta confirmar la división", "    Entonces intenta confirmar la división")))
                .anyMatch(d -> d.contains("esperaba «Cuando intenta confirmar la división»"));
    }

    @Test
    void aTagThatWouldSkipAScenarioIsNoticed() throws IOException {
        assertThat(differencesAfter("US16", text -> text.replace("  Escenario: 2:", "  @ignorar\n  Escenario: 2:")))
                .anyMatch(d -> d.contains("tiene etiquetas"));
        assertThat(differencesAfter("US16", text -> text.replace("Característica:", "@wip\nCaracterística:")))
                .anyMatch(d -> d.contains("la Característica tiene etiquetas"));
    }

    @Test
    void aChangedDescriptionAndAChangedTitleAreNoticed() throws IOException {
        assertThat(differencesAfter("US19", text -> text.replace("deseo registrar el desperdicio", "quiero registrar el desperdicio")))
                .anyMatch(d -> d.startsWith("US19: la descripción difiere"));
        assertThat(differencesAfter("US19", text -> text.replace("Registrar desperdicio", "Registrar mermas")))
                .anyMatch(d -> d.startsWith("US19: la Característica difiere"));
    }

    @Test
    void aMissingFeatureFileAndAnUnexpectedOneAreNoticed() throws IOException {
        Path copy = featuresCopy();
        Files.delete(feature(copy, "US42"));
        Files.writeString(copy.resolve("us11-privacidad.feature"), Files.readString(FEATURES.resolve(feature(FEATURES, "US10").getFileName())));
        assertThat(UserStoriesComparator.differences(DOCS, copy)).contains("US42: falta el .feature")
                .anyMatch(d -> d.contains("US11") && d.contains("no es de Production"));
    }

    @Test
    void aStoryScenarioChangedInTheDocumentationWithoutRegeneratingIsNoticed() throws IOException {
        Path docs = temp.resolve("user-stories.md");
        String text = Files.readString(DOCS);
        String edited = text.replace("  Entonces el sistema crea el lote como Disponible y No revisado", "  Entonces el sistema crea el lote como Disponible y Revisado");
        assertThat(edited).isNotEqualTo(text);
        Files.writeString(docs, edited);

        assertThat(UserStoriesComparator.differences(docs, FEATURES)).anyMatch(d -> d.startsWith("US12 Escenario 1, paso 4"));
    }

    @Test
    void whitespaceAndUnicodeFormAreNotDifferences() throws IOException {
        Path copy = featuresCopy();
        Path file = feature(copy, "US14");
        String original = "    Dado que existe un lote registrado con un código QR asociado";
        String text = Files.readString(file);
        assertThat(text).contains(original);
        // espacios de más, un espacio de no separación y el texto en forma NFD (la palabra clave del paso queda intacta)
        String changed = "      Dado   que   existe un lote\u00A0registrado con un " + Normalizer.normalize("código", Normalizer.Form.NFD) + " QR  asociado  ";
        Files.writeString(file, text.replace(original, changed));

        assertThat(UserStoriesComparator.differences(DOCS, copy)).isEmpty();
    }

    @Test
    void aFeatureThatCannotBeParsedIsReportedNotIgnored() throws IOException {
        assertThat(differencesAfter("US10", text -> text.replace("Característica:", "Caracteristicas de mentira:")))
                .isNotEmpty();
    }
}
