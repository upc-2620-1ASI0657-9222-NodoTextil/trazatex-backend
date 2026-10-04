package com.nodotextil.trazatex.traceability.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LineageEdgeTest {

    @Test
    void aBatchCannotBeItsOwnAncestor() {
        UUID batchId = UUID.randomUUID();

        assertThatThrownBy(() -> new LineageEdge(
                batchId, batchId, LineageType.DIVISION, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("own ancestor");
    }
}
