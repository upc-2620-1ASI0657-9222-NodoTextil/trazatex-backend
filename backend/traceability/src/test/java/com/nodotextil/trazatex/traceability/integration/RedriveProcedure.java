package com.nodotextil.trazatex.traceability.integration;

import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class RedriveProcedure {

    static final Path PROGRESS = Path.of("..", "docs", "progress.md");
    static final String COMPOSE_BOOTSTRAP = "localhost:9092";
    static final String TESTCONTAINERS_BOOTSTRAP = "localhost:9093";

    private RedriveProcedure() {
    }

    public static List<String> commands() {
        try {
            String text = Files.readString(PROGRESS);
            String block = text.substring(text.indexOf("<!-- redrive:begin -->"), text.indexOf("<!-- redrive:end -->"));
            return block.lines().map(String::strip).filter(line -> line.startsWith("docker compose")).toList();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el procedimiento de " + PROGRESS, e);
        }
    }

    static void copyScript(KafkaContainer kafka) {
        String[] tokens = commands().get(0).split("\\s+");
        if (tokens.length != 5 || !tokens[2].equals("cp") || !tokens[4].startsWith("kafka:")) {
            throw new IllegalStateException("El primer comando del procedimiento no es un 'docker compose cp <origen> kafka:<destino>': " + commands().get(0));
        }
        kafka.copyFileToContainer(MountableFile.forHostPath(Path.of("..").resolve(tokens[3])), tokens[4].substring("kafka:".length()));
    }

    static ExecResult run(KafkaContainer kafka, UUID failureId) {
        String line = commands().get(1);
        String prefix = "docker compose exec -T kafka ";
        if (!line.startsWith(prefix)) {
            throw new IllegalStateException("El segundo comando del procedimiento no es un 'docker compose exec -T kafka ...': " + line);
        }
        List<String> command = new ArrayList<>(Arrays.asList(line.substring(prefix.length()).split("\\s+")));
        command.replaceAll(token -> token.equals("<failureId>") ? failureId.toString() : token.equals(COMPOSE_BOOTSTRAP) ? TESTCONTAINERS_BOOTSTRAP : token);
        try {
            return kafka.execInContainer(command.toArray(String[]::new));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    static ExecResult runScript(KafkaContainer kafka, String... arguments) {
        List<String> command = new ArrayList<>(List.of("sh", "/tmp/redrive-dlt.sh"));
        command.addAll(List.of(arguments));
        try {
            return kafka.execInContainer(command.toArray(String[]::new));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
