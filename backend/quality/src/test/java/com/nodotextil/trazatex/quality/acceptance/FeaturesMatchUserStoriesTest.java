package com.nodotextil.trazatex.quality.acceptance;

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
 * {@code .feature} a mano, cambia la historia sin regenerar, o falta algún escenario de US23 a US28, US34, US43 o US44, esta prueba falla. Las pruebas de
 * abajo comprueban la prueba: aplican cada tipo de cambio a una copia y exigen que se note.
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
    void theFeaturesAreExactlyTheScenariosOfUs23ToUs28Us34Us43AndUs44() throws IOException {
        assertThat(UserStoriesComparator.differences(DOCS, FEATURES)).isEmpty();
    }

    @Test
    void thereAreNineStoriesAndTwentyScenariosAndNothingElse() throws IOException {
        assertThat(UserStoriesComparator.STORIES).containsExactly("US23", "US24", "US25", "US26", "US27", "US28", "US34", "US43", "US44");
        try (Stream<Path> files = Files.list(FEATURES)) {
            List<Path> list = files.toList();
            assertThat(list).hasSize(9);
            long scenarios = 0;
            for (Path file : list) {
                scenarios += Files.readAllLines(file).stream().filter(line -> line.trim().startsWith("Escenario:")).count();
            }
            assertThat(scenarios).isEqualTo(20);
        }
    }

    // ---- la prueba de la prueba ----

    @Test
    void aMissingScenarioIsNoticed() throws IOException {
        assertThat(differencesAfter("US34", text -> text.substring(0, text.indexOf("  Escenario: 4:")).stripTrailing() + "\n"))
                .anyMatch(d -> d.startsWith("US34: falta el Escenario 4:"));
    }

    @Test
    void anExtraScenarioIsNoticed() throws IOException {
        assertThat(differencesAfter("US24", text -> text + "\n  Escenario: 2: Uno de más\n    Dado que algo\n    Cuando ocurre\n    Entonces pasa.\n"))
                .anyMatch(d -> d.startsWith("US24: sobra el escenario"));
    }

    @Test
    void aSingleChangedWordInAStepIsNoticed() throws IOException {
        assertThat(differencesAfter("US25", text -> text.replace("devuelve la fase del lote a Disponible", "regresa la fase del lote a Disponible")))
                .anyMatch(d -> d.startsWith("US25 Escenario 1, paso") && d.contains("esperaba «Y devuelve la fase del lote a Disponible"));
    }

    @Test
    void aChangedScenarioTitleIsNoticed() throws IOException {
        assertThat(differencesAfter("US26", text -> text.replace("Enviar a reevaluación", "Enviar a revisión")))
                .anyMatch(d -> d.startsWith("US26 Escenario 2: el título difiere"));
    }

    @Test
    void aMissingAndAnExtraStepAreNoticed() throws IOException {
        assertThat(differencesAfter("US27", text -> text.replace("    Y registra la Falla como Confirmada.\n", "")))
                .anyMatch(d -> d.startsWith("US27 Escenario 1: falta el paso 4"));
        assertThat(differencesAfter("US27", text -> text.replace("    Y registra la Falla como Confirmada.\n", "    Y registra la Falla como Confirmada.\n    Y hace algo más.\n")))
                .anyMatch(d -> d.startsWith("US27 Escenario 1: sobra el paso 5"));
    }

    @Test
    void reorderedStepsAndAChangedKeywordAreNoticed() throws IOException {
        assertThat(differencesAfter("US23", text -> text.replace("    Cuando el administrador inicia un control\n    Entonces el sistema crea una Evaluación\n",
                "    Entonces el sistema crea una Evaluación\n    Cuando el administrador inicia un control\n"))).isNotEmpty();
        assertThat(differencesAfter("US23", text -> text.replace("    Cuando intenta iniciar otro", "    Entonces intenta iniciar otro")))
                .anyMatch(d -> d.contains("esperaba «Cuando intenta iniciar otro»"));
    }

    @Test
    void aTagThatWouldSkipAScenarioIsNoticed() throws IOException {
        assertThat(differencesAfter("US28", text -> text.replace("  Escenario: 2:", "  @ignorar\n  Escenario: 2:")))
                .anyMatch(d -> d.contains("tiene etiquetas"));
        assertThat(differencesAfter("US28", text -> text.replace("Característica:", "@wip\nCaracterística:")))
                .anyMatch(d -> d.contains("la Característica tiene etiquetas"));
    }

    @Test
    void aChangedDescriptionAndAChangedTitleAreNoticed() throws IOException {
        assertThat(differencesAfter("US24", text -> text.replace("deseo registrar las pruebas", "quiero registrar las pruebas")))
                .anyMatch(d -> d.startsWith("US24: la descripción difiere"));
        assertThat(differencesAfter("US24", text -> text.replace("Registrar pruebas de calidad", "Registrar mediciones")))
                .anyMatch(d -> d.startsWith("US24: la Característica difiere"));
    }

    @Test
    void aMissingFeatureFileAndAnUnexpectedOneAreNoticed() throws IOException {
        Path copy = featuresCopy();
        Files.delete(feature(copy, "US34"));
        Files.writeString(copy.resolve("us11-privacidad.feature"), Files.readString(FEATURES.resolve(feature(FEATURES, "US23").getFileName())));
        assertThat(UserStoriesComparator.differences(DOCS, copy)).contains("US34: falta el .feature")
                .anyMatch(d -> d.contains("US11") && d.contains("no es de Quality"));
    }

    @Test
    void aStoryScenarioChangedInTheDocumentationWithoutRegeneratingIsNoticed() throws IOException {
        Path docs = temp.resolve("user-stories.md");
        String text = Files.readString(DOCS);
        String edited = text.replace("Entonces registra fecha y hora de finalización", "Entonces registra fecha de finalización");
        assertThat(edited).isNotEqualTo(text);
        Files.writeString(docs, edited);

        assertThat(UserStoriesComparator.differences(docs, FEATURES)).anyMatch(d -> d.startsWith("US25 Escenario 1, paso 3"));
    }

    @Test
    void whitespaceAndUnicodeFormAreNotDifferences() throws IOException {
        Path copy = featuresCopy();
        Path file = feature(copy, "US23");
        String original = "    Dado que el lote posee un control activo";
        String text = Files.readString(file);
        assertThat(text).contains(original);
        // espacios de más, un espacio de no separación y el texto en forma NFD (la palabra clave del paso queda intacta)
        String changed = "      Dado   que   el lote posee un " + Normalizer.normalize("control", Normalizer.Form.NFD) + " activo  ";
        Files.writeString(file, text.replace(original, changed));

        assertThat(UserStoriesComparator.differences(DOCS, copy)).isEmpty();
    }

    @Test
    void aFeatureThatCannotBeParsedIsReportedNotIgnored() throws IOException {
        assertThat(differencesAfter("US23", text -> text.replace("Característica:", "Caracteristicas de mentira:"))).isNotEmpty();
    }
}
