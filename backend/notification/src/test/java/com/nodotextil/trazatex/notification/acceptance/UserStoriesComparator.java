package com.nodotextil.trazatex.notification.acceptance;

import io.cucumber.gherkin.GherkinParser;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.FeatureChild;
import io.cucumber.messages.types.GherkinDocument;
import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.Step;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compara los {@code .feature} ejecutables con {@code docs/domain/user-stories.md}: por historia, el título, la descripción y, de cada
 * escenario, el título y cada paso (palabra clave y texto) en el mismo orden. El texto se normaliza (Unicode NFC y espacios
 * colapsados) pero nada más: una palabra distinta, un paso de más o de menos, un escenario faltante o sobrante, otro orden, una
 * etiqueta que lo saltaría o un archivo que falta son diferencias. Lo único que cambia respecto del documento es la sintaxis
 * obligatoria de Gherkin: {@code Escenario 1: Título} se escribe {@code Escenario: 1: Título}.
 */
public final class UserStoriesComparator {

    /** La historia de Notification con escenarios ejecutables: US35. */
    public static final List<String> STORIES = List.of("US35");

    private static final Pattern SECTION = Pattern.compile("(?m)^### (US\\d+) — (.+)$");
    private static final Pattern SCENARIO_IN_DOCS = Pattern.compile("^Escenario (\\d+): (.+)$");

    private record DocScenario(String name, List<String> steps) {
    }

    private record DocStory(String title, String description, List<DocScenario> scenarios) {
    }

    private UserStoriesComparator() {
    }

    static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFC).replaceAll("[\\s\\u00A0\\u2007\\u202F]+", " ").trim();
    }

    /** @return las diferencias, una por línea; vacío si los {@code .feature} son exactamente los escenarios de las historias */
    public static List<String> differences(Path userStories, Path featuresDir) throws IOException {
        List<String> differences = new ArrayList<>();
        Map<String, DocStory> docs = storiesInDocs(userStories, differences);
        Map<String, Path> files = featureFiles(featuresDir, differences);
        for (String id : STORIES) {
            DocStory story = docs.get(id);
            if (story == null) {
                differences.add(id + ": la historia no está en user-stories.md");
                continue;
            }
            Path file = files.get(id);
            if (file == null) {
                differences.add(id + ": falta el .feature");
                continue;
            }
            compareStory(id, story, file, differences);
        }
        for (String id : files.keySet()) {
            if (!STORIES.contains(id)) {
                differences.add(id + ": hay un .feature de una historia que no es de Notification");
            }
        }
        return differences;
    }

    private static Map<String, Path> featureFiles(Path dir, List<String> differences) throws IOException {
        Map<String, Path> files = new LinkedHashMap<>();
        try (Stream<Path> list = Files.list(dir)) {
            for (Path file : list.sorted().toList()) {
                String name = file.getFileName().toString();
                Matcher id = Pattern.compile("^(us\\d+)-.+\\.feature$").matcher(name);
                if (!id.matches()) {
                    differences.add("archivo inesperado en features/: " + name);
                    continue;
                }
                if (files.put(id.group(1).toUpperCase(), file) != null) {
                    differences.add(id.group(1).toUpperCase() + ": hay más de un .feature");
                }
            }
        }
        return files;
    }

    private static Map<String, DocStory> storiesInDocs(Path userStories, List<String> differences) throws IOException {
        String text = Files.readString(userStories);
        List<Matcher> sections = new ArrayList<>();
        Matcher finder = SECTION.matcher(text);
        List<int[]> bounds = new ArrayList<>();
        List<String[]> heads = new ArrayList<>();
        while (finder.find()) {
            bounds.add(new int[]{finder.start(), finder.end()});
            heads.add(new String[]{finder.group(1), finder.group(2).trim()});
        }
        Map<String, DocStory> stories = new LinkedHashMap<>();
        for (int i = 0; i < heads.size(); i++) {
            String id = heads.get(i)[0];
            if (!STORIES.contains(id)) {
                continue;
            }
            String body = text.substring(bounds.get(i)[1], i + 1 < heads.size() ? bounds.get(i + 1)[0] : text.length());
            Matcher description = Pattern.compile("(?m)^(Como .+)$").matcher(body);
            Matcher gherkin = Pattern.compile("(?s)```gherkin\\n(.*?)```").matcher(body);
            if (!description.find() || !gherkin.find()) {
                differences.add(id + ": user-stories.md no tiene descripción o bloque gherkin");
                continue;
            }
            List<DocScenario> scenarios = new ArrayList<>();
            String name = null;
            List<String> steps = new ArrayList<>();
            for (String raw : gherkin.group(1).lines().toList()) {
                String line = normalize(raw);
                if (line.isEmpty()) {
                    continue;
                }
                Matcher scenario = SCENARIO_IN_DOCS.matcher(line);
                if (scenario.matches()) {
                    if (name != null) {
                        scenarios.add(new DocScenario(name, steps));
                    }
                    name = scenario.group(1) + ": " + scenario.group(2);
                    steps = new ArrayList<>();
                } else {
                    steps.add(line);
                }
            }
            if (name != null) {
                scenarios.add(new DocScenario(name, steps));
            }
            stories.put(id, new DocStory(heads.get(i)[1], normalize(description.group(1)), scenarios));
        }
        return stories;
    }

    private static void compareStory(String id, DocStory story, Path file, List<String> differences) throws IOException {
        Feature feature = parse(file, differences, id);
        if (feature == null) {
            return;
        }
        if (!"es".equals(feature.getLanguage())) {
            differences.add(id + ": el .feature debe estar en español (# language: es), no en '" + feature.getLanguage() + "'");
        }
        if (!normalize(feature.getName()).equals(normalize(id + " — " + story.title()))) {
            differences.add(id + ": la Característica difiere: esperaba «" + id + " — " + story.title() + "» y dice «" + feature.getName() + "»");
        }
        if (!normalize(feature.getDescription()).equals(story.description())) {
            differences.add(id + ": la descripción difiere: esperaba «" + story.description() + "» y dice «" + normalize(feature.getDescription()) + "»");
        }
        if (!feature.getTags().isEmpty()) {
            differences.add(id + ": la Característica tiene etiquetas (" + feature.getTags().size() + "): una etiqueta puede saltarse escenarios");
        }
        List<Scenario> scenarios = new ArrayList<>();
        for (FeatureChild child : feature.getChildren()) {
            if (child.getScenario().isPresent()) {
                scenarios.add(child.getScenario().get());
            } else {
                differences.add(id + ": solo se admiten escenarios (hay Antecedentes o Reglas)");
            }
        }
        for (int i = 0; i < Math.max(story.scenarios().size(), scenarios.size()); i++) {
            if (i >= scenarios.size()) {
                differences.add(id + ": falta el Escenario " + story.scenarios().get(i).name());
                continue;
            }
            if (i >= story.scenarios().size()) {
                differences.add(id + ": sobra el escenario «" + scenarios.get(i).getName() + "»");
                continue;
            }
            compareScenario(id, story.scenarios().get(i), scenarios.get(i), differences);
        }
    }

    private static void compareScenario(String id, DocScenario expected, Scenario actual, List<String> differences) {
        String where = id + " Escenario " + expected.name().substring(0, expected.name().indexOf(':'));
        if (!normalize(actual.getKeyword()).equals("Escenario")) {
            differences.add(where + ": debe ser un 'Escenario', no '" + actual.getKeyword() + "'");
        }
        if (!normalize(actual.getName()).equals(expected.name())) {
            differences.add(where + ": el título difiere: esperaba «" + expected.name() + "» y dice «" + normalize(actual.getName()) + "»");
        }
        if (!actual.getTags().isEmpty()) {
            differences.add(where + ": tiene etiquetas: una etiqueta puede saltarse el escenario");
        }
        if (!actual.getExamples().isEmpty()) {
            differences.add(where + ": no puede tener ejemplos (Esquema del escenario)");
        }
        List<Step> steps = actual.getSteps();
        for (int i = 0; i < Math.max(expected.steps().size(), steps.size()); i++) {
            if (i >= steps.size()) {
                differences.add(where + ": falta el paso " + (i + 1) + ": «" + expected.steps().get(i) + "»");
            } else if (i >= expected.steps().size()) {
                differences.add(where + ": sobra el paso " + (i + 1) + ": «" + normalize(steps.get(i).getKeyword() + steps.get(i).getText()) + "»");
            } else {
                String found = normalize(steps.get(i).getKeyword() + steps.get(i).getText());
                if (!found.equals(expected.steps().get(i))) {
                    differences.add(where + ", paso " + (i + 1) + ": esperaba «" + expected.steps().get(i) + "» y dice «" + found + "»");
                }
                if (steps.get(i).getDocString().isPresent() || steps.get(i).getDataTable().isPresent()) {
                    differences.add(where + ", paso " + (i + 1) + ": no admite tablas ni textos adjuntos");
                }
            }
        }
    }

    private static Feature parse(Path file, List<String> differences, String id) throws IOException {
        List<Envelope> envelopes;
        try (Stream<Envelope> stream = GherkinParser.builder().includeSource(false).includePickles(false).build().parse(file)) {
            envelopes = stream.collect(Collectors.toList());
        }
        for (Envelope envelope : envelopes) {
            if (envelope.getParseError().isPresent()) {
                differences.add(id + ": el .feature no se puede leer: " + envelope.getParseError().get().getMessage());
                return null;
            }
        }
        return envelopes.stream().map(Envelope::getGherkinDocument).flatMap(java.util.Optional::stream).map(GherkinDocument::getFeature)
                .flatMap(java.util.Optional::stream).findFirst().orElseGet(() -> {
                    differences.add(id + ": el archivo no tiene una Característica");
                    return null;
                });
    }
}
