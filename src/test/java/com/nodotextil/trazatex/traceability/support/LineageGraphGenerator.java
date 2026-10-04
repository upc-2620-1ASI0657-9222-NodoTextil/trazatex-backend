package com.nodotextil.trazatex.traceability.support;

import com.nodotextil.trazatex.traceability.domain.LineageEdge;
import com.nodotextil.trazatex.traceability.domain.LineageRepository;
import com.nodotextil.trazatex.traceability.domain.LineageType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class LineageGraphGenerator {

    public static final int LEVELS = 10;
    public static final int NODES_PER_LEVEL = 50;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 20, 14, 0);

    private final List<List<UUID>> levels = new ArrayList<>();

    public LineageGraphGenerator populate(LineageRepository repository) {
        Random random = new Random(42);
        for (int level = 0; level < LEVELS; level++) {
            List<UUID> nodes = new ArrayList<>();
            for (int i = 0; i < NODES_PER_LEVEL; i++) {
                nodes.add(UUID.randomUUID());
            }
            levels.add(nodes);
        }
        for (int level = 1; level < LEVELS; level++) {
            for (int i = 0; i < NODES_PER_LEVEL; i++) {
                UUID child = levels.get(level).get(i);
                link(repository, levels.get(level - 1).get(i), child, random);
                if (random.nextBoolean()) {
                    int other = random.nextInt(NODES_PER_LEVEL);
                    if (other != i) {
                        link(repository, levels.get(level - 1).get(other), child, random);
                    }
                }
            }
        }
        return this;
    }

    private static void link(LineageRepository repository, UUID parent, UUID child, Random random) {
        LineageType type = random.nextBoolean() ? LineageType.DIVISION : LineageType.TRANSFORMATION;
        repository.saveEdge(new LineageEdge(parent, child, type, NOW));
    }

    public int totalNodes() {
        return LEVELS * NODES_PER_LEVEL;
    }

    public UUID deepestBatch(int index) {
        return levels.get(LEVELS - 1).get(index % NODES_PER_LEVEL);
    }

    public UUID rootBatch(int index) {
        return levels.get(0).get(index % NODES_PER_LEVEL);
    }
}
