package com.nodotextil.trazatex.traceability.acceptance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * {@code .feature} a mano, cambia la historia sin regenerar, o falta algún escenario de US29–US32, esta prueba falla. Las
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
    void theFeaturesAreExactlyTheScenariosOfEveryStory() throws IOException {
        assertThat(UserStoriesComparator.differences(DOCS, FEATURES)).isEmpty();
    }

    @Test
    void thereAreSixStoriesAndTwelveScenariosAndNothingElse() throws IOException {
        assertThat(UserStoriesComparator.STORIES).containsExactly("US29", "US30", "US31", "US32", "US33", "US36");
        try (Stream<Path> files = Files.list(FEATURES)) {
            List<Path> list = files.toList();
            assertThat(list).hasSize(6);
            long scenarios = 0;
            for (Path file : list) {
                scenarios += Files.readAllLines(file).stream().filter(line -> line.trim().startsWith("Escenario:")).count();
            }
            assertThat(scenarios).isEqualTo(3 + 1 + 1 + 1 + 4 + 2);
        }
    }

    // ---- lo mismo para cada historia nueva: la prueba de la prueba no se hizo solo para US29 ----

    @ParameterizedTest
    @ValueSource(strings = {"US30", "US31", "US32", "US33", "US36"})
    void aChangedWordInAnyStepOfTheOtherStoriesIsNoticed(String id) throws IOException {
        String step = Files.readAllLines(feature(FEATURES, id)).stream().filter(line -> line.trim().startsWith("Entonces ")).findFirst().orElseThrow();
        String changed = step.replaceFirst("Entonces ", "Entonces además ");
        assertThat(changed).isNotEqualTo(step);

        assertThat(differencesAfter(id, text -> text.replace(step, changed))).anyMatch(d -> d.startsWith(id + " Escenario 1, paso 3: esperaba"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"US30", "US31", "US32", "US33", "US36"})
    void aMissingStepAndAMissingScenarioOfTheOtherStoriesAreNoticed(String id) throws IOException {
        assertThat(differencesAfter(id, text -> text.substring(0, text.indexOf("  Escenario: 1:")).stripTrailing() + "\n"))
                .anyMatch(d -> d.startsWith(id + ": falta el Escenario 1:"));
        // se quita el primer paso «Dado» (el primero del primer escenario): el último paso de ese escenario queda sin su pareja
        assertThat(differencesAfter(id, text -> {
            String given = text.lines().filter(line -> line.trim().startsWith("Dado ")).findFirst().orElseThrow();
            return text.replace(given + "\n", "");
        })).anyMatch(d -> d.startsWith(id + " Escenario 1: falta el paso"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"US30", "US31", "US32", "US33", "US36"})
    void aChangeInTheDocumentationOfOneStoryIsNoticedOnlyInThatStory(String id) throws IOException {
        Path docs = temp.resolve("user-stories-" + id + ".md");
        String text = Files.readString(DOCS);
        int gherkin = text.indexOf("Entonces", text.indexOf("### " + id + " — "));
        Files.writeString(docs, text.substring(0, gherkin) + "Entonces algo distinto" + text.substring(text.indexOf('\n', gherkin)));

        assertThat(UserStoriesComparator.differences(docs, FEATURES)).anyMatch(d -> d.startsWith(id + " Escenario 1, paso 3")).noneMatch(d -> d.startsWith("US29"));
    }

    // ---- la prueba de la prueba ----

    @Test
    void aMissingScenarioIsNoticed() throws IOException {
        assertThat(differencesAfter("US29", text -> text.substring(0, text.indexOf("  Escenario: 3:")).stripTrailing() + "\n"))
                .anyMatch(d -> d.startsWith("US29: falta el Escenario 3:"));
    }

    @Test
    void anExtraScenarioIsNoticed() throws IOException {
        assertThat(differencesAfter("US29", text -> text + "\n  Escenario: 4: Uno de más\n    Dado que algo\n    Cuando ocurre\n    Entonces pasa.\n"))
                .anyMatch(d -> d.startsWith("US29: sobra el escenario"));
    }

    @Test
    void aSingleChangedWordInAStepIsNoticed() throws IOException {
        assertThat(differencesAfter("US29", text -> text.replace("conserva la transferencia dentro del historial", "guarda la transferencia dentro del historial")))
                .anyMatch(d -> d.startsWith("US29 Escenario 3, paso 3: esperaba «Entonces el sistema conserva la transferencia dentro del historial del mismo lote»"));
    }

    @Test
    void aChangedScenarioTitleIsNoticed() throws IOException {
        assertThat(differencesAfter("US29", text -> text.replace("Relación por división", "Relación por partición")))
                .anyMatch(d -> d.startsWith("US29 Escenario 1: el título difiere"));
    }

    @Test
    void aMissingAndAnExtraStepAreNoticed() throws IOException {
        assertThat(differencesAfter("US29", text -> text.replace("    Y no crea un nuevo lote ni una relación genealógica padre-hijo.\n", "")))
                .anyMatch(d -> d.startsWith("US29 Escenario 3: falta el paso 4"));
        assertThat(differencesAfter("US29", text -> text.replace("    Y no crea un nuevo lote ni una relación genealógica padre-hijo.\n",
                "    Y no crea un nuevo lote ni una relación genealógica padre-hijo.\n    Y hace algo más.\n")))
                .anyMatch(d -> d.startsWith("US29 Escenario 3: sobra el paso 5"));
    }

    @Test
    void reorderedStepsAndAChangedKeywordAreNoticed() throws IOException {
        assertThat(differencesAfter("US29", text -> text.replace("    Cuando se generan sus hijos\n    Entonces el sistema registra la relación padre-hijo.\n",
                "    Entonces el sistema registra la relación padre-hijo.\n    Cuando se generan sus hijos\n"))).isNotEmpty();
        assertThat(differencesAfter("US29", text -> text.replace("    Cuando se generan sus hijos", "    Entonces se generan sus hijos")))
                .anyMatch(d -> d.contains("esperaba «Cuando se generan sus hijos»"));
    }

    @Test
    void aTagThatWouldSkipAScenarioIsNoticed() throws IOException {
        assertThat(differencesAfter("US29", text -> text.replace("  Escenario: 2:", "  @ignorar\n  Escenario: 2:")))
                .anyMatch(d -> d.contains("tiene etiquetas"));
        assertThat(differencesAfter("US29", text -> text.replace("Característica:", "@wip\nCaracterística:")))
                .anyMatch(d -> d.contains("la Característica tiene etiquetas"));
    }

    @Test
    void aChangedDescriptionAndAChangedTitleAreNoticed() throws IOException {
        assertThat(differencesAfter("US29", text -> text.replace("deseo que el sistema genere automáticamente", "quiero que el sistema genere automáticamente")))
                .anyMatch(d -> d.startsWith("US29: la descripción difiere"));
        assertThat(differencesAfter("US29", text -> text.replace("Generar genealogía", "Generar árboles")))
                .anyMatch(d -> d.startsWith("US29: la Característica difiere"));
    }

    @Test
    void aMissingFeatureFileAndAnUnexpectedOneAreNoticed() throws IOException {
        Path copy = featuresCopy();
        Files.delete(feature(copy, "US29"));
        Files.writeString(copy.resolve("us11-privacidad.feature"), Files.readString(FEATURES.resolve(feature(FEATURES, "US29").getFileName())));
        assertThat(UserStoriesComparator.differences(DOCS, copy)).contains("US29: falta el .feature")
                .anyMatch(d -> d.contains("US11") && d.contains("no es de Traceability"));
    }

    @Test
    void aStoryScenarioChangedInTheDocumentationWithoutRegeneratingIsNoticed() throws IOException {
        Path docs = temp.resolve("user-stories.md");
        String text = Files.readString(DOCS);
        String edited = text.replace("  Entonces el sistema registra la relación padre-hijo.", "  Entonces el sistema registra la relación hijo-padre.");
        assertThat(edited).isNotEqualTo(text);
        Files.writeString(docs, edited);

        assertThat(UserStoriesComparator.differences(docs, FEATURES)).anyMatch(d -> d.startsWith("US29 Escenario 1, paso 3"));
    }

    @Test
    void whitespaceAndUnicodeFormAreNotDifferences() throws IOException {
        Path copy = featuresCopy();
        Path file = feature(copy, "US29");
        String original = "    Entonces el sistema registra la relación padre-hijo.";
        String text = Files.readString(file);
        assertThat(text).contains(original);
        // espacios de más, un espacio de no separación y el texto en forma NFD (la palabra clave del paso queda intacta)
        String changed = "      Entonces   el sistema\u00A0registra la " + Normalizer.normalize("relación", Normalizer.Form.NFD) + " padre-hijo.  ";
        Files.writeString(file, text.replace(original, changed));

        assertThat(UserStoriesComparator.differences(DOCS, copy)).isEmpty();
    }

    @Test
    void aFeatureThatCannotBeParsedIsReportedNotIgnored() throws IOException {
        assertThat(differencesAfter("US29", text -> text.replace("Característica:", "Caracteristicas de mentira:")))
                .isNotEmpty();
    }
}
