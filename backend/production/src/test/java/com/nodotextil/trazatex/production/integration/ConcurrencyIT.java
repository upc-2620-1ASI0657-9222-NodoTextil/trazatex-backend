package com.nodotextil.trazatex.production.integration;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.BatchFactory;
import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.CompanyProjection;
import com.nodotextil.trazatex.production.domain.model.CompanyStatus;
import com.nodotextil.trazatex.production.domain.model.Machine;
import com.nodotextil.trazatex.production.domain.model.Quantity;
import com.nodotextil.trazatex.production.domain.model.Transfer;
import com.nodotextil.trazatex.production.domain.model.Transformation;
import com.nodotextil.trazatex.production.domain.model.TransformationType;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.nodotextil.trazatex.production.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.OPERATOR;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las operaciones sobre un mismo lote se serializan con {@code SELECT ... FOR UPDATE} (los lotes, siempre en orden de
 * id): de dos operaciones simultáneas sobre el mismo lote prospera una y la otra ve el estado nuevo y recibe el
 * conflicto del dominio (409), no un error de la base ni de bloqueo optimista. Cada escenario se repite 8 veces.
 *
 * <p>Los métodos {@code startTransformation}, {@code split}... imitan lo que harán los casos de uso de 04b-2:
 * bloquear, aplicar el dominio y guardar, todo en una transacción.
 */
class ConcurrencyIT extends IntegrationTestBase {

    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-0000000000ad");
    private final CompanyProjection target = CompanyProjection.of(OTHER_COMPANY, "Tintorería Sur", CompanyStatus.ACTIVE, START);

    // ---- los "casos de uso" ----

    private List<Batch> lock(List<UUID> ids) {
        List<Batch> locked = batches.findAllForUpdate(ids);
        if (locked.size() != new java.util.HashSet<>(ids).size()) {
            throw new NotFoundException("Lote no encontrado");
        }
        return locked;
    }

    private Transformation startTransformation(UUID machineId, List<UUID> batchIds) {
        return inTx(() -> {
            Machine machine = machines.findById(machineId).orElseThrow();
            List<Batch> locked = lock(batchIds);
            Transformation transformation = transformationFactory.start(UUID.randomUUID(), TransformationType.SPINNING, COMPANY,
                    machine, OPERATOR, locked, START);
            batches.saveAll(locked);
            transformations.save(transformation);
            return transformation;
        });
    }

    private BatchFactory.SplitResult split(UUID batchId) {
        return inTx(() -> {
            Batch parent = lock(List.of(batchId)).get(0);
            BatchFactory.SplitResult result = batchFactory.split(parent, List.of(
                    new BatchFactory.ChildSpec(null, Quantity.ofKg("40")),
                    new BatchFactory.ChildSpec(null, Quantity.ofKg("60"))), OPERATOR, START);
            batches.saveAll(result.children());
            batches.save(parent);
            return result;
        });
    }

    private Transfer initiate(List<UUID> batchIds, CompanyProjection to) {
        return inTx(() -> {
            List<Batch> locked = lock(batchIds);
            Transfer transfer = Transfer.initiate(UUID.randomUUID(), COMPANY, to, locked, OPERATOR, START);
            batches.saveAll(locked);
            transfers.save(transfer);
            return transfer;
        });
    }

    private void resolve(UUID transferId, boolean accept) {
        inTx(() -> {
            Transfer transfer = transfers.findByIdForUpdate(transferId).orElseThrow();
            List<Batch> locked = lock(transfer.batchIds());
            if (accept) {
                transfer.accept(OTHER_COMPANY, locked, ADMIN, START.plusSeconds(60));
            } else {
                transfer.reject(OTHER_COMPANY, locked, "No lo necesitamos", ADMIN, START.plusSeconds(60));
            }
            batches.saveAll(locked);
            transfers.save(transfer);
        });
    }

    private List<Batch> complete(UUID transformationId) {
        return inTx(() -> {
            Transformation transformation = transformations.findByIdForUpdate(transformationId).orElseThrow();
            List<Batch> locked = lock(transformation.inputs().stream().map(i -> i.batchId()).toList());
            List<Batch> produced = transformation.finalize(locked, List.of(
                    new BatchFactory.OutputSpec(null, "Hilo", Quantity.ofKg("90"), false, null)), null, null, batchFactory, OPERATOR, START);
            batches.saveAll(locked);
            batches.saveAll(produced);
            transformations.save(transformation);
            return produced;
        });
    }

    // ---- carrera ----

    /** Lanza todas las tareas a la vez y devuelve, por tarea, su resultado o la excepción que lanzó. */
    private List<Object> race(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CyclicBarrier start = new CyclicBarrier(tasks.size());
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        return task.call();
                    } catch (Throwable e) {
                        return e;
                    }
                }));
            }
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private static List<Object> failures(List<Object> outcomes) {
        return outcomes.stream().filter(Throwable.class::isInstance).toList();
    }

    private static void assertOneWinner(List<Object> outcomes) {
        assertThat(outcomes.size() - failures(outcomes).size()).as("exactamente una operación prospera: %s", outcomes).isEqualTo(1);
        assertThat(failures(outcomes)).as("las demás reciben el conflicto del dominio, no un error de base o bloqueo")
                .allSatisfy(failure -> assertThat(failure).isInstanceOf(ConflictException.class));
    }

    // ---- escenarios ----

    @RepeatedTest(8)
    void twoTransformationsOverTheSameBatchOnlyOneStarts() throws Exception {
        Machine machine = savedMachine(COMPANY, "MQ-" + UUID.randomUUID().toString().substring(0, 8));
        Batch batch = savedBatch("100");

        List<Object> outcomes = race(List.of(
                () -> startTransformation(machine.id(), List.of(batch.id())),
                () -> startTransformation(machine.id(), List.of(batch.id()))));

        assertOneWinner(outcomes);
        assertThat(((ConflictException) failures(outcomes).get(0)).code()).isEqualTo(ErrorCodes.INVALID_PHASE_TRANSITION);
        assertThat(count("SELECT count(*) FROM transformations")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM transformation_inputs WHERE batch_id = ?", batch.id())).isEqualTo(1);
        assertThat(inTx(() -> batches.findById(batch.id()).orElseThrow()).phase()).isEqualTo(BatchPhase.IN_TRANSFORMATION);
    }

    @RepeatedTest(8)
    void sixConcurrentStartsOverTheSameBatchOnlyOneStarts() throws Exception {
        Machine machine = savedMachine(COMPANY, "MQ-" + UUID.randomUUID().toString().substring(0, 8));
        Batch batch = savedBatch("100");
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> startTransformation(machine.id(), List.of(batch.id())));
        }

        List<Object> outcomes = race(tasks);

        assertOneWinner(outcomes);
        assertThat(count("SELECT count(*) FROM transformations")).isEqualTo(1);
    }

    @RepeatedTest(8)
    void twoSplitsOfTheSameBatchOnlyOneDivides() throws Exception {
        Batch parent = savedBatch("100");

        List<Object> outcomes = race(List.of(() -> split(parent.id()), () -> split(parent.id())));

        assertOneWinner(outcomes);
        assertThat(inTx(() -> batches.findById(parent.id()).orElseThrow()).phase()).isEqualTo(BatchPhase.SPLIT);
        assertThat(count("SELECT count(*) FROM batches WHERE origin_type = 'SPLIT'")).as("un solo juego de hijos").isEqualTo(2);
        assertThat(count("SELECT sum(quantity_kg) FROM batches WHERE origin_type = 'SPLIT'")).isEqualTo(100);
    }

    @RepeatedTest(8)
    void aSplitAndATransformationOfTheSameBatchOnlyOneWins() throws Exception {
        Machine machine = savedMachine(COMPANY, "MQ-" + UUID.randomUUID().toString().substring(0, 8));
        Batch batch = savedBatch("100");

        List<Object> outcomes = race(List.of(() -> split(batch.id()), () -> startTransformation(machine.id(), List.of(batch.id()))));

        assertOneWinner(outcomes);
        BatchPhase phase = inTx(() -> batches.findById(batch.id()).orElseThrow()).phase();
        assertThat(phase).isIn(BatchPhase.SPLIT, BatchPhase.IN_TRANSFORMATION);
        assertThat(count("SELECT count(*) FROM transformations")).isEqualTo(phase == BatchPhase.IN_TRANSFORMATION ? 1 : 0);
        assertThat(count("SELECT count(*) FROM batches WHERE origin_type = 'SPLIT'")).isEqualTo(phase == BatchPhase.SPLIT ? 2 : 0);
    }

    @RepeatedTest(8)
    void twoTransfersOfTheSameBatchOnlyOneStarts() throws Exception {
        Batch batch = savedBatch("100");
        CompanyProjection third = CompanyProjection.of(UUID.randomUUID(), "Acabados Este", CompanyStatus.ACTIVE, START);

        List<Object> outcomes = race(List.of(() -> initiate(List.of(batch.id()), target), () -> initiate(List.of(batch.id()), third)));

        assertOneWinner(outcomes);
        assertThat(count("SELECT count(*) FROM transfers")).isEqualTo(1);
        assertThat(inTx(() -> batches.findById(batch.id()).orElseThrow()).phase()).isEqualTo(BatchPhase.IN_TRANSFER);
    }

    @RepeatedTest(8)
    void acceptingAndRejectingTheSameTransferOnlyOneResolvesIt() throws Exception {
        Batch batch = savedBatch("100");
        Transfer transfer = initiate(List.of(batch.id()), target);

        List<Object> outcomes = race(List.of(() -> {
            resolve(transfer.id(), true);
            return "accepted";
        }, () -> {
            resolve(transfer.id(), false);
            return "rejected";
        }));

        assertOneWinner(outcomes);
        Transfer resolved = inTx(() -> transfers.findById(transfer.id()).orElseThrow());
        Batch moved = inTx(() -> batches.findById(batch.id()).orElseThrow());
        assertThat(moved.phase()).isEqualTo(BatchPhase.AVAILABLE);
        String winner = (String) outcomes.stream().filter(o -> !(o instanceof Throwable)).findFirst().orElseThrow();
        if (winner.equals("accepted")) {
            assertThat(resolved.status().name()).isEqualTo("ACCEPTED");
            assertThat(moved.companyId()).isEqualTo(OTHER_COMPANY);
        } else {
            assertThat(resolved.status().name()).isEqualTo("REJECTED");
            assertThat(moved.companyId()).isEqualTo(COMPANY);
        }
    }

    @RepeatedTest(8)
    void completingTheSameTransformationTwiceOnlyCreatesOneSetOfOutputs() throws Exception {
        Machine machine = savedMachine(COMPANY, "MQ-" + UUID.randomUUID().toString().substring(0, 8));
        Batch batch = savedBatch("100");
        Transformation started = startTransformation(machine.id(), List.of(batch.id()));

        List<Object> outcomes = race(List.of(() -> complete(started.id()), () -> complete(started.id())));

        assertOneWinner(outcomes);
        assertThat(count("SELECT count(*) FROM batches WHERE origin_type = 'TRANSFORMATION'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM transformation_outputs")).isEqualTo(1);
        assertThat(inTx(() -> batches.findById(batch.id()).orElseThrow()).phase()).isEqualTo(BatchPhase.PROCESSED);
    }

    @RepeatedTest(8)
    void overlappingBatchSetsAskedInDifferentOrdersNeverDeadlock() throws Exception {
        Machine machine = savedMachine(COMPANY, "MQ-" + UUID.randomUUID().toString().substring(0, 8));
        List<UUID> pool = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            pool.add(savedBatch("10").id());
        }
        Random random = new Random(42 + pool.hashCode());
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            List<UUID> mine = new ArrayList<>(pool);
            Collections.shuffle(mine, random);                       // cada uno pide los mismos lotes en otro orden
            tasks.add(() -> startTransformation(machine.id(), mine));
        }

        List<Object> outcomes = race(tasks);

        assertOneWinner(outcomes);
        assertThat(failures(outcomes)).noneMatch(f -> f instanceof org.springframework.dao.DeadlockLoserDataAccessException);
        assertThat(count("SELECT count(*) FROM transformation_inputs")).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM batches WHERE current_stage = 'IN_TRANSFORMATION'")).isEqualTo(5);
    }

    @RepeatedTest(8)
    void partiallyOverlappingTransformationsNeverUseTheSameBatchTwice() throws Exception {
        Machine machine = savedMachine(COMPANY, "MQ-" + UUID.randomUUID().toString().substring(0, 8));
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ids.add(savedBatch("10").id());
        }
        List<Callable<Object>> tasks = List.of(
                () -> startTransformation(machine.id(), List.of(ids.get(0), ids.get(1), ids.get(2))),
                () -> startTransformation(machine.id(), List.of(ids.get(3), ids.get(2), ids.get(1))),
                () -> startTransformation(machine.id(), List.of(ids.get(1), ids.get(3))));

        List<Object> outcomes = race(tasks);

        assertThat(outcomes.size() - failures(outcomes).size()).isGreaterThanOrEqualTo(1);
        assertThat(failures(outcomes)).allSatisfy(failure -> assertThat(failure).isInstanceOf(ConflictException.class));
        assertThat(count("SELECT count(*) FROM (SELECT batch_id FROM transformation_inputs GROUP BY batch_id HAVING count(*) > 1) d"))
                .as("ningún lote entra a dos transformaciones").isZero();
        assertThat(count("SELECT count(*) FROM transformation_inputs"))
                .isEqualTo(count("SELECT count(*) FROM batches WHERE current_stage = 'IN_TRANSFORMATION'"));
    }

    @Test
    void independentBatchesDoNotBlockEachOther() throws Exception {
        Machine machine = savedMachine(COMPANY, "MQ-01");
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            UUID id = savedBatch("10").id();
            tasks.add(() -> startTransformation(machine.id(), List.of(id)));
        }

        List<Object> outcomes = race(tasks);

        assertThat(failures(outcomes)).isEmpty();
        assertThat(count("SELECT count(*) FROM transformations")).isEqualTo(6);
    }
}
