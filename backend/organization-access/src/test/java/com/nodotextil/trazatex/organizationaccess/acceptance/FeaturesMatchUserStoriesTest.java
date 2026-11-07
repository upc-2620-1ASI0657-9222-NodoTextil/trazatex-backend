package com.nodotextil.trazatex.organizationaccess.acceptance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los escenarios ejecutables (RNF-10) tienen que ser el texto exacto de {@code docs/domain/user-stories.md}: cada paso, en el
 * mismo orden y sin cambios. Si alguien edita un {@code .feature} a mano, o cambia la historia sin regenerar, esta prueba falla.
 * US10 (maquinaria) no está: es de Production.
 */
class FeaturesMatchUserStoriesTest {

    private static final Path DOCS = Path.of("..", "docs", "domain", "user-stories.md");
    private static final Path FEATURES = Path.of("src", "test", "resources", "features");
    private static final List<String> STORIES = List.of("US01", "US02", "US03", "US04", "US05", "US06", "US07", "US08", "US09", "US11", "US41");
    private static final Pattern SECTION = Pattern.compile("(?m)^### (US\\d+) — (.+)$");
    private static final Pattern SCENARIO_IN_DOCS = Pattern.compile("^Escenario (\\d+): (.+)$");
    private static final Pattern SCENARIO_IN_FEATURE = Pattern.compile("^Escenario: (\\d+): (.+)$");

    /** Por historia: título, descripción ("Como...") y las líneas del bloque gherkin tal como están en la documentación. */
    private record Story(String title, String description, List<String> lines) {
    }

    private static Map<String, Story> storiesInDocs() throws IOException {
        String text = Files.readString(DOCS);
        Matcher sections = SECTION.matcher(text);
        List<int[]> bounds = new ArrayList<>();
        List<String[]> heads = new ArrayList<>();
        while (sections.find()) {
            bounds.add(new int[]{sections.start(), sections.end()});
            heads.add(new String[]{sections.group(1), sections.group(2).trim()});
        }
        Map<String, Story> stories = new LinkedHashMap<>();
        for (int i = 0; i < heads.size(); i++) {
            String id = heads.get(i)[0];
            if (!STORIES.contains(id)) {
                continue;
            }
            String body = text.substring(bounds.get(i)[1], i + 1 < heads.size() ? bounds.get(i + 1)[0] : text.length());
            Matcher description = Pattern.compile("(?m)^(Como .+)$").matcher(body);
            Matcher gherkin = Pattern.compile("(?s)```gherkin\\n(.*?)```").matcher(body);
            assertThat(description.find() && gherkin.find()).as("%s debe tener descripción y bloque gherkin", id).isTrue();
            List<String> lines = gherkin.group(1).lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
            stories.put(id, new Story(heads.get(i)[1], description.group(1).trim(), lines));
        }
        return stories;
    }

    private static Path featureOf(String id) throws IOException {
        try (var files = Files.list(FEATURES)) {
            List<Path> matches = files.filter(path -> path.getFileName().toString().startsWith(id.toLowerCase() + "-")).toList();
            assertThat(matches).as("debe haber exactamente un .feature para %s", id).hasSize(1);
            return matches.get(0);
        }
    }

    @Test
    void thereIsAFeatureForEveryStoryOfTheServiceAndNoneForUs10() throws IOException {
        assertThat(storiesInDocs()).containsOnlyKeys(STORIES);
        try (var files = Files.list(FEATURES)) {
            assertThat(files.map(path -> path.getFileName().toString().substring(0, 4).toUpperCase()).sorted().toList())
                    .containsExactlyElementsOf(STORIES);
        }
    }

    @Test
    void everyFeatureIsInSpanishAndKeepsTheStoryDescription() throws IOException {
        for (Map.Entry<String, Story> story : storiesInDocs().entrySet()) {
            List<String> lines = Files.readAllLines(featureOf(story.getKey()));
            assertThat(lines.get(0)).as(story.getKey()).isEqualTo("# language: es");
            assertThat(lines.get(1)).as(story.getKey()).isEqualTo("Característica: " + story.getKey() + " — " + story.getValue().title());
            assertThat(lines.get(2).trim()).as(story.getKey()).isEqualTo(story.getValue().description());
        }
    }

    @Test
    void everyScenarioAndEveryStepIsTheExactTextOfTheUserStories() throws IOException {
        for (Map.Entry<String, Story> story : storiesInDocs().entrySet()) {
            List<String> inFeature = new ArrayList<>();
            for (String raw : Files.readAllLines(featureOf(story.getKey())).subList(3, Files.readAllLines(featureOf(story.getKey())).size())) {
                String line = raw.trim();
                if (line.isEmpty()) {
                    continue;
                }
                Matcher scenario = SCENARIO_IN_FEATURE.matcher(line);
                inFeature.add(scenario.matches() ? "Escenario " + scenario.group(1) + ": " + scenario.group(2) : line);
            }
            assertThat(inFeature).as("%s: el .feature debe ser idéntico al bloque gherkin de user-stories.md", story.getKey())
                    .containsExactlyElementsOf(story.getValue().lines());
            assertThat(story.getValue().lines().stream().filter(line -> SCENARIO_IN_DOCS.matcher(line).matches()).count())
                    .as("%s tiene escenarios", story.getKey()).isPositive();
        }
    }

    @Test
    void thirtyScenariosAreExecutable() throws IOException {
        long scenarios = 0;
        for (String id : STORIES) {
            scenarios += Files.readAllLines(featureOf(id)).stream().filter(line -> line.trim().startsWith("Escenario:")).count();
        }
        assertThat(scenarios).isEqualTo(30);
    }
}
