package com.nodotextil.trazatex.production.application.port.out;

import com.nodotextil.trazatex.production.application.port.out.TransferSearchCriteria.Direction;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El tope de 100 filas por página también está en los criterios de búsqueda: ninguna consulta lo supera, venga de donde venga. */
class SearchCriteriaTest {

    private static final UUID COMPANY = UUID.randomUUID();

    @Test
    void everyCriteriaCapsTheSizeAt100() {
        assertThat(new BatchSearchCriteria(COMPANY, null, null, null, null, null, null, 0, 100_000).size()).isEqualTo(100);
        assertThat(new TransformationSearchCriteria(COMPANY, null, null, 0, 101).size()).isEqualTo(100);
        assertThat(new TransferSearchCriteria(COMPANY, Direction.ALL, null, 0, Integer.MAX_VALUE).size()).isEqualTo(100);
    }

    @Test
    void sizesUpToTheCapAreKept() {
        assertThat(new BatchSearchCriteria(COMPANY, null, null, null, null, null, null, 0, 100).size()).isEqualTo(100);
        assertThat(new BatchSearchCriteria(COMPANY, null, null, null, null, null, null, 0, 1).size()).isEqualTo(1);
        assertThat(new TransformationSearchCriteria(COMPANY, null, null, 3, 37).size()).isEqualTo(37);
        assertThat(new TransferSearchCriteria(COMPANY, Direction.INCOMING, null, 2, 20).page()).isEqualTo(2);
    }

    @Test
    void impossiblePagesAreRejected() {
        assertThatThrownBy(() -> new BatchSearchCriteria(COMPANY, null, null, null, null, null, null, -1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BatchSearchCriteria(COMPANY, null, null, null, null, null, null, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransformationSearchCriteria(COMPANY, null, null, 0, -5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransferSearchCriteria(COMPANY, Direction.ALL, null, -2, 20)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thePageReportsItsTotalPages() {
        assertThat(new Page<>(java.util.List.of(), 0, 100, 0).totalPages()).isZero();
        assertThat(new Page<>(java.util.List.of(), 0, 100, 100).totalPages()).isEqualTo(1);
        assertThat(new Page<>(java.util.List.of(), 0, 100, 101).totalPages()).isEqualTo(2);
        assertThat(new Page<>(java.util.List.of(1, 2), 0, 3, 7).map(i -> i * 2).items()).containsExactly(2, 4);
    }
}
