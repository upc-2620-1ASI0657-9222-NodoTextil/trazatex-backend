package com.nodotextil.trazatex.sharedkernel.paging;

import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageRequestTest {

    @Test
    void withoutParametersItIsTheFirstPageOfTwentyAndTooBigASizeIsCutToOneHundred() {
        assertThat(PageRequest.of(null, null)).isEqualTo(new PageRequest(0, 20));
        assertThat(PageRequest.of(3, 100)).isEqualTo(new PageRequest(3, 100));
        assertThat(PageRequest.of(0, 101).size()).isEqualTo(100);
        assertThat(PageRequest.of(0, Integer.MAX_VALUE).size()).isEqualTo(100);
    }

    @Test
    void aNegativePageOrASizeBelowOneIsInvalidPagination() {
        assertThatThrownBy(() -> PageRequest.of(-1, 10)).isInstanceOfSatisfying(ValidationException.class, e -> {
            assertThat(e.code()).isEqualTo("INVALID_PAGINATION");
            assertThat(e.details()).containsExactly("page");
        });
        assertThatThrownBy(() -> PageRequest.of(0, 0)).isInstanceOfSatisfying(ValidationException.class, e -> {
            assertThat(e.code()).isEqualTo("INVALID_PAGINATION");
            assertThat(e.details()).containsExactly("size");
        });
    }

    @Test
    void theOffsetDoesNotOverflow() {
        assertThat(new PageRequest(Integer.MAX_VALUE, 100).offset()).isEqualTo(214_748_364_700L);
    }

    @Test
    void thePageCountRoundsUpAndAnEmptyResultHasNoPages() {
        PageRequest request = new PageRequest(1, 20);
        assertThat(PageResult.of(List.of("a"), request, 41).totalPages()).isEqualTo(3);
        assertThat(PageResult.of(List.of("a"), request, 40).totalPages()).isEqualTo(2);
        assertThat(PageResult.of(List.<String>of(), request, 0).totalPages()).isZero();
        assertThat(PageResult.of(List.of("a"), request, 41).map(String::toUpperCase).items()).containsExactly("A");
    }
}
