package com.nodotextil.trazatex.analytics.acceptance;

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
 * {@code .feature} a mano, cambia la historia sin regenerar, o falta algún escenario de US37 a US40, esta prueba falla. Las pruebas de abajo
 * comprueban la prueba: aplican cada tipo de cambio a una copia y exigen que se note.
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
    void theFeaturesAreExactlyTheScenariosOfUs37ToUs40() throws IOException {
        assertThat(UserStoriesComparator.differences(DOCS, FEATURES)).isEmpty();
    }

    @Test
    void thereAreFourStoriesAndFourScenariosAndNothingElse() throws IOException {
        assertThat(UserStoriesComparator.STORIES).containsExactly("US37", "US38", "US39", "US40");
        try (Stream<Path> files = Files.list(FEATURES)) {
            List<Path> list = files.toList();
            assertThat(list).hasSize(4);
            long scenarios = 0;
            for (Path file : list) {
                scenarios += Files.readAllLines(file).stream().filter(line -> line.trim().startsWith("Escenario:")).count();
            }
            assertThat(scenarios).isEqualTo(4);
        }
    }

    // ---- la prueba de la prueba ----

    @Test
    void aMissingScenarioIsNoticed() throws IOException {
        assertThat(differencesAfter("US37", text -> text.substring(0, text.indexOf("  Escenario: 1:")).stripTrailing() + "\n"))
                .anyMatch(d -> d.startsWith("US37: falta el Escenario 1:"));
    }

    @Test
    void anExtraScenarioIsNoticed() throws IOException {
        assertThat(differencesAfter("US38", text -> text + "\n  Escenario: 2: Uno de más\n    Dado que algo\n    Cuando ocurre\n    Entonces pasa.\n"))
                .anyMatch(d -> d.startsWith("US38: sobra el escenario"));
    }

    @Test
    void aSingleChangedWordInAStepIsNoticed() throws IOException {
        assertThat(differencesAfter("US39", text -> text.replace("la duración promedio de transformaciones", "la duración media de transformaciones")))
                .anyMatch(d -> d.startsWith("US39 Escenario 1, paso 3") && d.contains("esperaba «Entonces el sistema muestra la duración promedio"));
    }

    @Test
    void aChangedScenarioTitleIsNoticed() throws IOException {
        assertThat(differencesAfter("US37", text -> text.replace("Consulta por periodo", "Consulta por rango")))
                .anyMatch(d -> d.startsWith("US37 Escenario 1: el título difiere"));
    }

    @Test
    void aMissingAndAnExtraStepAreNoticed() throws IOException {
        assertThat(differencesAfter("US37", text -> text.replace("    Y presenta los resultados del periodo seleccionado.\n", "")))
                .anyMatch(d -> d.startsWith("US37 Escenario 1: falta el paso 4"));
        assertThat(differencesAfter("US37", text -> text.replace("    Y presenta los resultados del periodo seleccionado.\n",
                "    Y presenta los resultados del periodo seleccionado.\n    Y hace algo más.\n")))
                .anyMatch(d -> d.startsWith("US37 Escenario 1: sobra el paso 5"));
    }

    @Test
    void reorderedStepsAndAChangedKeywordAreNoticed() throws IOException {
        assertThat(differencesAfter("US38", text -> text.replace(
                "    Cuando el administrador consulta el análisis\n    Entonces el sistema muestra la cantidad de fallas y sus causas más frecuentes\n",
                "    Entonces el sistema muestra la cantidad de fallas y sus causas más frecuentes\n    Cuando el administrador consulta el análisis\n"))).isNotEmpty();
        assertThat(differencesAfter("US38", text -> text.replace("    Cuando el administrador consulta el análisis", "    Entonces el administrador consulta el análisis")))
                .anyMatch(d -> d.contains("esperaba «Cuando el administrador consulta el análisis»"));
    }

    @Test
    void aTagThatWouldSkipAScenarioIsNoticed() throws IOException {
        assertThat(differencesAfter("US40", text -> text.replace("  Escenario: 1:", "  @ignorar\n  Escenario: 1:")))
                .anyMatch(d -> d.contains("tiene etiquetas"));
        assertThat(differencesAfter("US40", text -> text.replace("Característica:", "@wip\nCaracterística:")))
                .anyMatch(d -> d.contains("la Característica tiene etiquetas"));
    }

    @Test
    void aChangedDescriptionAndAChangedTitleAreNoticed() throws IOException {
        assertThat(differencesAfter("US39", text -> text.replace("deseo consultar los tiempos promedio", "quiero consultar los tiempos promedio")))
                .anyMatch(d -> d.startsWith("US39: la descripción difiere"));
        assertThat(differencesAfter("US39", text -> text.replace("Analizar tiempos operativos", "Analizar duraciones")))
                .anyMatch(d -> d.startsWith("US39: la Característica difiere"));
    }

    @Test
    void aMissingFeatureFileAndAnUnexpectedOneAreNoticed() throws IOException {
        Path copy = featuresCopy();
        Files.delete(feature(copy, "US40"));
        Files.writeString(copy.resolve("us11-privacidad.feature"), Files.readString(FEATURES.resolve(feature(FEATURES, "US37").getFileName())));
        assertThat(UserStoriesComparator.differences(DOCS, copy)).contains("US40: falta el .feature")
                .anyMatch(d -> d.contains("US11") && d.contains("no es de Analytics"));
    }

    @Test
    void aStoryScenarioChangedInTheDocumentationWithoutRegeneratingIsNoticed() throws IOException {
        Path docs = temp.resolve("user-stories.md");
        String text = Files.readString(DOCS);
        String edited = text.replace("Entonces el sistema muestra la merma acumulada y el desperdicio acumulado", "Entonces el sistema muestra la merma y el desperdicio acumulados");
        assertThat(edited).isNotEqualTo(text);
        Files.writeString(docs, edited);

        assertThat(UserStoriesComparator.differences(docs, FEATURES)).anyMatch(d -> d.startsWith("US40 Escenario 1, paso 3"));
    }

    @Test
    void whitespaceAndUnicodeFormAreNotDifferences() throws IOException {
        Path copy = featuresCopy();
        Path file = feature(copy, "US37");
        String original = "    Dado que existen registros dentro del periodo seleccionado";
        String text = Files.readString(file);
        assertThat(text).contains(original);
        // espacios de más y el texto en forma NFD (la palabra clave del paso queda intacta)
        String changed = "      Dado   que   existen   " + Normalizer.normalize("registros", Normalizer.Form.NFD) + " dentro del periodo seleccionado  ";
        Files.writeString(file, text.replace(original, changed));

        assertThat(UserStoriesComparator.differences(DOCS, copy)).isEmpty();
    }

    @Test
    void aFeatureThatCannotBeParsedIsReportedNotIgnored() throws IOException {
        assertThat(differencesAfter("US37", text -> text.replace("Característica:", "Caracteristicas de mentira:"))).isNotEmpty();
    }
}
