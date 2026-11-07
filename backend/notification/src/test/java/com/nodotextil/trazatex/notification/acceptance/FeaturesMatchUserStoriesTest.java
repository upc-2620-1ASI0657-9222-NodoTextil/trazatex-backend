package com.nodotextil.trazatex.notification.acceptance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El escenario ejecutable de Notification (US35) es el texto exacto de {@code docs/domain/user-stories.md}: título y cada paso. Si alguien edita
 * el {@code .feature} a mano o cambia la historia sin regenerar, esta prueba falla. Las pruebas de abajo comprueban la prueba: aplican cada tipo
 * de cambio a una copia y exigen que se note.
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

    private Path feature(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(path -> path.getFileName().toString().startsWith("us35-")).findFirst().orElseThrow();
        }
    }

    private List<String> differencesAfter(UnaryOperator<String> edit) throws IOException {
        Path copy = featuresCopy();
        Path file = feature(copy);
        String edited = edit.apply(Files.readString(file));
        assertThat(edited).as("la edición de la prueba debe cambiar algo").isNotEqualTo(Files.readString(file));
        Files.writeString(file, edited);
        return UserStoriesComparator.differences(DOCS, copy);
    }

    @Test
    void theFeatureIsExactlyTheScenarioOfUs35() throws IOException {
        assertThat(UserStoriesComparator.STORIES).containsExactly("US35");
        assertThat(UserStoriesComparator.differences(DOCS, FEATURES)).isEmpty();
        try (Stream<Path> files = Files.list(FEATURES)) {
            assertThat(files.toList()).hasSize(1);
        }
    }

    @Test
    void aChangedWordAMissingStepAndAChangedTitleAreNoticed() throws IOException {
        assertThat(differencesAfter(text -> text.replace("genera una alerta para los administradores responsables", "genera un aviso para los administradores responsables")))
                .anyMatch(d -> d.startsWith("US35 Escenario 1, paso 3"));
        assertThat(differencesAfter(text -> text.replace("    Y permite identificar el lote que requiere revisión.\n", "")))
                .anyMatch(d -> d.startsWith("US35 Escenario 1: falta el paso 4"));
        assertThat(differencesAfter(text -> text.replace("Generación de alerta", "Generación de aviso")))
                .anyMatch(d -> d.startsWith("US35 Escenario 1: el título difiere"));
    }

    @Test
    void aMissingScenarioAnExtraOneAndATagAreNoticed() throws IOException {
        assertThat(differencesAfter(text -> text.substring(0, text.indexOf("  Escenario: 1:")).stripTrailing() + "\n"))
                .anyMatch(d -> d.startsWith("US35: falta el Escenario 1:"));
        assertThat(differencesAfter(text -> text + "\n  Escenario: 2: Uno de más\n    Dado que algo\n    Cuando ocurre\n    Entonces pasa.\n"))
                .anyMatch(d -> d.startsWith("US35: sobra el escenario"));
        assertThat(differencesAfter(text -> text.replace("  Escenario: 1:", "  @ignorar\n  Escenario: 1:"))).anyMatch(d -> d.contains("tiene etiquetas"));
    }

    @Test
    void aStoryChangedInTheDocumentationWithoutRegeneratingAndAMissingFileAreNoticed() throws IOException {
        Path docs = temp.resolve("user-stories.md");
        String text = Files.readString(DOCS);
        String edited = text.replace("Y permite identificar el lote que requiere revisión.", "Y permite identificar el lote.");
        assertThat(edited).isNotEqualTo(text);
        Files.writeString(docs, edited);
        assertThat(UserStoriesComparator.differences(docs, FEATURES)).anyMatch(d -> d.startsWith("US35 Escenario 1, paso 4"));

        Path copy = featuresCopy();
        Files.delete(feature(copy));
        assertThat(UserStoriesComparator.differences(DOCS, copy)).contains("US35: falta el .feature");
    }
}
