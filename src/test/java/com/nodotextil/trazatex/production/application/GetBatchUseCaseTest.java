package com.nodotextil.trazatex.production.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetBatchUseCaseTest {

    @Test
    void rejectsUnknownBatch() {
        UUID unknownId = UUID.fromString("45d43922-8a9b-47a2-808b-d22bb4476627");
        GetBatchUseCase useCase = new GetBatchUseCase(new InMemoryBatchRepository());

        assertThatThrownBy(() -> useCase.execute(unknownId))
                .isInstanceOf(BatchNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }
}
