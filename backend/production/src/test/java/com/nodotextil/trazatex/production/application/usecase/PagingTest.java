package com.nodotextil.trazatex.production.application.usecase;

import com.nodotextil.trazatex.production.application.port.out.Page;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RNF-03: el tamaño de página tiene un tope de 100; si piden más, se usa 100. */
class PagingTest {

    @Test
    void theDefaultsAreFirstPageAndTwentyRows() {
        assertThat(Paging.page(null)).isZero();
        assertThat(Paging.size(null)).isEqualTo(20).isEqualTo(Page.DEFAULT_SIZE);
    }

    @Test
    void theMaximumIs100() {
        assertThat(Page.MAX_SIZE).isEqualTo(100);
        assertThat(Paging.size(100)).isEqualTo(100);
        assertThat(Paging.size(101)).isEqualTo(100);
        assertThat(Paging.size(500)).isEqualTo(100);
        assertThat(Paging.size(Integer.MAX_VALUE)).isEqualTo(100);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 20, 50, 99})
    void smallerSizesAreRespected(int size) {
        assertThat(Paging.size(size)).isEqualTo(size);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100, Integer.MIN_VALUE})
    void aSizeBelowOneIsRejected(int size) {
        assertThatThrownBy(() -> Paging.size(size)).isInstanceOfSatisfying(TrazatexException.class,
                e -> assertThat(e.code()).isEqualTo("INVALID_PAGINATION"));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -50, Integer.MIN_VALUE})
    void aNegativePageIsRejected(int page) {
        assertThatThrownBy(() -> Paging.page(page)).isInstanceOfSatisfying(TrazatexException.class,
                e -> assertThat(e.code()).isEqualTo("INVALID_PAGINATION"));
    }

    @Test
    void anyNonNegativePageIsAccepted() {
        assertThat(Paging.page(0)).isZero();
        assertThat(Paging.page(7)).isEqualTo(7);
        assertThat(Paging.page(Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
    }
}
