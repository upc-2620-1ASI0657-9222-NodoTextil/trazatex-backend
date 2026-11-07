package com.nodotextil.trazatex.production.integration;

import com.nodotextil.trazatex.production.application.port.out.Page;
import com.nodotextil.trazatex.production.application.port.out.TransformationSearchCriteria;
import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.BatchFactory;
import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.Machine;
import com.nodotextil.trazatex.production.domain.model.Quantity;
import com.nodotextil.trazatex.production.domain.model.Transformation;
import com.nodotextil.trazatex.production.domain.model.TransformationStatus;
import com.nodotextil.trazatex.production.domain.model.TransformationType;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.nodotextil.trazatex.production.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.OPERATOR;
import static com.nodotextil.trazatex.production.domain.Fixtures.composition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransformationRepositoryIT extends IntegrationTestBase {

    /**
     * Lo que hará el caso de uso: bloquear los lotes (que vuelven en orden de id), reponerlos en el orden pedido por el
     * operario, iniciar en el dominio y guardar todo en una transacción.
     */
    private Transformation start(TransformationType type, Machine machine, Batch... inputs) {
        return inTx(() -> {
            List<UUID> ids = java.util.Arrays.stream(inputs).map(Batch::id).toList();
            List<Batch> byId = batches.findAllForUpdate(ids);
            List<Batch> locked = ids.stream().map(id -> byId.stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow()).toList();
            Transformation transformation = transformationFactory.start(UUID.randomUUID(), type, COMPANY, machine, OPERATOR, locked, START);
            batches.saveAll(locked);
            transformations.save(transformation);
            return transformation;
        });
    }

    @Test
    void aStartedTransformationRoundTripsAndItsInputsAreInProcess() {
        Machine machine = savedMachine(COMPANY, "MQ-01");
        Batch a = savedBatch("100");
        Batch b = savedBatch(composition("Lana", "100"), "50.5");

        Transformation started = start(TransformationType.SPINNING, machine, a, b);

        Transformation loaded = inTx(() -> transformations.findById(started.id()).orElseThrow());
        assertThat(loaded.id()).isEqualTo(started.id());
        assertThat(loaded.companyId()).isEqualTo(COMPANY);
        assertThat(loaded.type()).isEqualTo(TransformationType.SPINNING);
        assertThat(loaded.machineId()).isEqualTo(machine.id());
        assertThat(loaded.operatorId()).isEqualTo(OPERATOR);
        assertThat(loaded.status()).isEqualTo(TransformationStatus.IN_PROGRESS);
        assertThat(loaded.startTime()).isEqualTo(START);
        assertThat(loaded.inputs()).extracting(i -> i.batchId()).containsExactly(a.id(), b.id());
        assertThat(loaded.inputs()).extracting(i -> i.quantity()).containsExactly(Quantity.ofKg("100"), Quantity.ofKg("50.5"));
        assertThat(loaded.totalInput()).isEqualTo(Quantity.ofKg("150.5"));
        assertThat(loaded.isMix()).isTrue();
        assertThat(loaded.totalOutput()).isNull();
        assertThat(loaded.waste()).isNull();
        assertThat(loaded.shrinkage()).isNull();
        assertThat(loaded.endTime()).isNull();
        assertThat(loaded.outputBatchIds()).isEmpty();
        assertThat(inTx(() -> batches.findById(a.id()).orElseThrow()).phase()).isEqualTo(BatchPhase.IN_TRANSFORMATION);
    }

    @Test
    void aCompletedTransformationRoundTripsItsBalanceAndOutputs() {
        Machine machine = savedMachine(COMPANY, "MQ-01");
        Batch input = savedBatch("100");
        Transformation started = start(TransformationType.WEAVING, machine, input);

        List<Batch> outputs = inTx(() -> {
            Transformation transformation = transformations.findByIdForUpdate(started.id()).orElseThrow();
            List<Batch> locked = batches.findAllForUpdate(List.of(input.id()));
            List<Batch> produced = transformation.finalize(locked, List.of(
                    new BatchFactory.OutputSpec("Tela A", "Tela", Quantity.ofKg("60"), false, null),
                    new BatchFactory.OutputSpec(null, "Tela", Quantity.ofKg("25.250"), false, null)),
                    Quantity.ofKg("5"), "Recorte de orillo", batchFactory, OPERATOR, START.plus(Duration.ofHours(3)));
            batches.saveAll(locked);
            batches.saveAll(produced);
            transformations.save(transformation);
            return produced;
        });

        Transformation loaded = inTx(() -> transformations.findById(started.id()).orElseThrow());
        assertThat(loaded.status()).isEqualTo(TransformationStatus.COMPLETED);
        assertThat(loaded.totalInput()).isEqualTo(Quantity.ofKg("100"));
        assertThat(loaded.totalOutput()).isEqualTo(Quantity.ofKg("85.25"));
        assertThat(loaded.waste()).isEqualTo(Quantity.ofKg("5"));
        assertThat(loaded.wasteReason()).isEqualTo("Recorte de orillo");
        assertThat(loaded.shrinkage()).as("100 − 85,25 − 5").isEqualTo(Quantity.ofKg("9.75"));
        assertThat(loaded.endTime()).isEqualTo(START.plus(Duration.ofHours(3)));
        assertThat(loaded.outputBatchIds()).containsExactly(outputs.get(0).id(), outputs.get(1).id());
        assertThat(inTx(() -> batches.findById(input.id()).orElseThrow()).phase()).isEqualTo(BatchPhase.PROCESSED);
        assertThat(inTx(() -> batches.findById(outputs.get(0).id()).orElseThrow()).phase()).isEqualTo(BatchPhase.AVAILABLE);
    }

    @Test
    void aTransformationWithoutWasteStoresNoReason() {
        Machine machine = savedMachine(COMPANY, "MQ-01");
        Batch input = savedBatch("100");
        Transformation started = start(TransformationType.DYEING, machine, input);

        inTx(() -> {
            Transformation transformation = transformations.findByIdForUpdate(started.id()).orElseThrow();
            List<Batch> locked = batches.findAllForUpdate(List.of(input.id()));
            List<Batch> produced = transformation.finalize(locked, List.of(
                    new BatchFactory.OutputSpec(null, "Tela", Quantity.ofKg("90"), false, null)), null, null, batchFactory, OPERATOR, START);
            batches.saveAll(locked);
            batches.saveAll(produced);
            transformations.save(transformation);
        });

        Transformation loaded = inTx(() -> transformations.findById(started.id()).orElseThrow());
        assertThat(loaded.waste().isZero()).isTrue();
        assertThat(loaded.wasteReason()).isNull();
        assertThat(loaded.shrinkage()).isEqualTo(Quantity.ofKg("10"));
    }

    @Test
    void theStrategyOfTheTypeIsRestoredWithTheConfiguredMinimum() {
        Machine machine = savedMachine(COMPANY, "MQ-01");
        Batch input = savedBatch("100");
        Transformation started = start(TransformationType.WEAVING, machine, input);

        // tras recargarla, la tejeduría sigue exigiendo el 80 % configurado: 79,9 kg de 100 se rechaza
        assertThatThrownBy(() -> inTx(() -> {
            Transformation transformation = transformations.findByIdForUpdate(started.id()).orElseThrow();
            List<Batch> locked = batches.findAllForUpdate(List.of(input.id()));
            transformation.finalize(locked, List.of(new BatchFactory.OutputSpec(null, "Tela", Quantity.ofKg("79.9"), false, null)),
                    null, null, batchFactory, OPERATOR, START);
        })).hasMessageContaining("mínimo de 80 %");
    }

    @Test
    void aBatchCannotBeConsumedByTwoTransformations() {
        Machine machine = savedMachine(COMPANY, "MQ-01");
        Batch input = savedBatch("100");
        start(TransformationType.SPINNING, machine, input);

        // saltándose la regla del dominio, la base lo impide igual
        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO transformation_inputs (transformation_id, position, batch_id, quantity_kg)
                        SELECT id, 5, :batch, 1 FROM transformations
                        """).param("batch", input.id()).update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void searchReturnsTheTransformationsOfTheCompanyNewestFirstWithFilters() {
        Machine machine = savedMachine(COMPANY, "MQ-01");
        Machine foreign = savedMachine(OTHER_COMPANY, "MQ-01");
        Transformation first = inTx(() -> {
            Batch batch = batchFactory.register(registration("10"), COMPANY, OPERATOR, START);
            batches.save(batch);
            Transformation t = transformationFactory.start(UUID.randomUUID(), TransformationType.SPINNING, COMPANY, machine, OPERATOR,
                    List.of(batch), START.minusSeconds(120));
            batches.save(batch);
            transformations.save(t);
            return t;
        });
        Transformation second = inTx(() -> {
            Batch batch = batchFactory.register(registration("10"), COMPANY, OPERATOR, START);
            batches.save(batch);
            Transformation t = transformationFactory.start(UUID.randomUUID(), TransformationType.DYEING, COMPANY, machine, OPERATOR,
                    List.of(batch), START);
            batches.save(batch);
            transformations.save(t);
            return t;
        });
        inTx(() -> {
            Batch batch = batchFactory.register(registration("10"), OTHER_COMPANY, OPERATOR, START);
            batches.save(batch);
            Transformation t = transformationFactory.start(UUID.randomUUID(), TransformationType.SPINNING, OTHER_COMPANY, foreign, OPERATOR,
                    List.of(batch), START);
            batches.save(batch);
            transformations.save(t);
        });

        Page<Transformation> all = inTx(() -> transformations.search(new TransformationSearchCriteria(COMPANY, null, null, 0, 20)));
        assertThat(all.items()).extracting(Transformation::id).containsExactly(second.id(), first.id());
        assertThat(all.totalElements()).isEqualTo(2);
        assertThat(inTx(() -> transformations.search(new TransformationSearchCriteria(COMPANY, null, TransformationType.SPINNING, 0, 20)))
                .items()).extracting(Transformation::id).containsExactly(first.id());
        assertThat(inTx(() -> transformations.search(new TransformationSearchCriteria(COMPANY, TransformationStatus.COMPLETED, null, 0, 20)))
                .items()).isEmpty();
        assertThat(inTx(() -> transformations.search(new TransformationSearchCriteria(COMPANY, null, null, 1, 1))).items())
                .extracting(Transformation::id).containsExactly(first.id());
    }

    @Test
    void anUnknownIdReturnsNothing() {
        assertThat(inTx(() -> transformations.findById(UUID.randomUUID()))).isEmpty();
        assertThat(inTx(() -> transformations.findByIdForUpdate(UUID.randomUUID()))).isEmpty();
    }
}
