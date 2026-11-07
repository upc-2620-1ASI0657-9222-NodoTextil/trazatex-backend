package com.nodotextil.trazatex.production.application.usecase;

import com.nodotextil.trazatex.production.application.port.out.BatchRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.ProductionEventPublisherPort;
import com.nodotextil.trazatex.production.domain.event.BatchPhaseChanged;
import com.nodotextil.trazatex.production.domain.event.ProductionEvent;
import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.Quantity;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nodotextil.trazatex.production.domain.Fixtures.batch;
import static com.nodotextil.trazatex.production.domain.Fixtures.cotton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code batchVersion} de {@code BatchPhaseChanged}: es la versión que devuelve la base tras guardar, por lote. */
class BatchChangesTest {

    private final BatchRepositoryPort batches = mock(BatchRepositoryPort.class);
    private final ProductionEventPublisherPort events = mock(ProductionEventPublisherPort.class);
    private final BatchChanges changes = new BatchChanges(batches, events);
    private final PhaseChangeCause cause = new PhaseChangeCause("TEST", "req-1");

    private static Batch available(UUID id) {
        return batch(BatchPhase.AVAILABLE, false, false, Quantity.ofKg("10"), cotton(), id, QualityStatus.NOT_REVIEWED);
    }

    @Test
    void eachEventCarriesTheVersionOfItsOwnBatch() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID b = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        Batch first = available(a);
        Batch second = available(b);
        first.hold();
        second.hold();
        when(batches.saveAll(any())).thenReturn(Map.of(a, 5L, b, 12L));

        changes.persistAndPublish(List.of(second, first), cause, null);

        ArgumentCaptor<ProductionEvent> published = ArgumentCaptor.forClass(ProductionEvent.class);
        verify(events, org.mockito.Mockito.times(2)).publish(published.capture());
        assertThat(published.getAllValues()).extracting(e -> ((BatchPhaseChanged) e).batchId(), e -> ((BatchPhaseChanged) e).batchVersion())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(a, 5L), org.assertj.core.groups.Tuple.tuple(b, 12L));
    }

    @Test
    void aBatchWithoutAVersionAfterSavingIsAnErrorNotAGuess() {
        UUID id = UUID.randomUUID();
        Batch batch = available(id);
        batch.hold();
        when(batches.saveAll(any())).thenReturn(Map.of());

        assertThatThrownBy(() -> changes.persistAndPublish(List.of(batch), cause, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("versión");
        verify(events, never()).publish(any());
    }

    @Test
    void twoChangesOfTheSameBatchInOneTransactionWouldShareAVersionSoTheyAreRejected() {
        UUID id = UUID.randomUUID();
        Batch batch = available(id);
        batch.hold();
        batch.releaseHold();
        when(batches.saveAll(any())).thenReturn(Map.of(id, 3L));

        assertThatThrownBy(() -> changes.persistAndPublish(List.of(batch), cause, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("batchVersion");
        verify(events, never()).publish(any());
    }
}
