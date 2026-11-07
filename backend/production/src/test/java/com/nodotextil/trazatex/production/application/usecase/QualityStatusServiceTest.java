package com.nodotextil.trazatex.production.application.usecase;

import com.nodotextil.trazatex.production.application.port.out.BatchRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.ProductionEventPublisherPort;
import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.nodotextil.trazatex.production.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.OTHER_COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.batch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QualityStatusServiceTest {

    private final BatchRepositoryPort batches = mock(BatchRepositoryPort.class);
    private final ProductionEventPublisherPort events = mock(ProductionEventPublisherPort.class);
    private final QualityStatusService service = new QualityStatusService(batches, new BatchChanges(batches, events));

    private Batch stored(BatchPhase phase, boolean hold) {
        Batch batch = batch(phase, hold, false, "10");
        when(batches.findById(batch.id())).thenReturn(Optional.of(batch));
        return batch;
    }

    @Test
    void itCopiesTheStatusAndNothingElseAndPublishesNothing() {
        Batch batch = stored(BatchPhase.AVAILABLE, false);

        service.apply(batch.id(), COMPANY, QualityStatus.FAILED, UUID.randomUUID());

        assertThat(batch.qualityStatus()).isEqualTo(QualityStatus.FAILED);
        assertThat(batch.phase()).isEqualTo(BatchPhase.AVAILABLE);
        assertThat(batch.qualityHold()).as("la retención la fija solo el comando interno").isFalse();
        verify(batches).saveAll(List.of(batch));
        verify(events, never()).publish(any());
    }

    @Test
    void theCopyIsAppliedInAnyPhaseWithoutTouchingThePhaseOrTheHold() {
        for (BatchPhase phase : BatchPhase.values()) {
            for (boolean hold : new boolean[]{false, true}) {
                Batch batch = stored(phase, hold);

                service.apply(batch.id(), COMPANY, QualityStatus.CONFORMING, UUID.randomUUID());

                assertThat(batch.qualityStatus()).isEqualTo(QualityStatus.CONFORMING);
                assertThat(batch.phase()).isEqualTo(phase);
                assertThat(batch.qualityHold()).isEqualTo(hold);
                assertThat(batch.hasPhaseChanges()).isFalse();
            }
        }
        verify(events, never()).publish(any());
    }

    @Test
    void theSameStatusIsNotSavedAgain() {
        Batch batch = stored(BatchPhase.AVAILABLE, false);
        service.apply(batch.id(), COMPANY, QualityStatus.NOT_REVIEWED, UUID.randomUUID());

        verify(batches, never()).saveAll(any());
    }

    @Test
    void aBatchThatChangedCompanyIsUpdatedAnyway() {
        Batch batch = stored(BatchPhase.AVAILABLE, false);

        service.apply(batch.id(), OTHER_COMPANY, QualityStatus.FAILED, UUID.randomUUID());

        assertThat(batch.qualityStatus()).isEqualTo(QualityStatus.FAILED);
    }

    @Test
    void aBatchThatDoesNotExistIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(batches.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.apply(missing, COMPANY, QualityStatus.FAILED, UUID.randomUUID()))
                .isInstanceOfSatisfying(NotFoundException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.BATCH_NOT_FOUND));
    }
}
