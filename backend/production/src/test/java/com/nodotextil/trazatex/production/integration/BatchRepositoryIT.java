package com.nodotextil.trazatex.production.integration;

import com.nodotextil.trazatex.production.application.port.out.BatchSearchCriteria;
import com.nodotextil.trazatex.production.application.port.out.Page;
import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.BatchFactory;
import com.nodotextil.trazatex.production.domain.model.BatchOriginType;
import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.CommercialData;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;
import com.nodotextil.trazatex.production.domain.model.QrCode;
import com.nodotextil.trazatex.production.domain.model.Quantity;
import com.nodotextil.trazatex.production.domain.model.TraceabilityId;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.nodotextil.trazatex.production.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.OPERATOR;
import static com.nodotextil.trazatex.production.domain.Fixtures.composition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchRepositoryIT extends IntegrationTestBase {

    // ---- ida y vuelta ----

    @Test
    void aRegisteredBatchRoundTripsEveryField() {
        Batch saved = savedBatch("250.5");

        Batch loaded = inTx(() -> batches.findById(saved.id()).orElseThrow());

        assertThat(loaded.id()).isEqualTo(saved.id());
        assertThat(loaded.traceabilityId()).isEqualTo(saved.traceabilityId());
        assertThat(loaded.qrCode()).isEqualTo(saved.qrCode());
        assertThat(loaded.companyId()).isEqualTo(COMPANY);
        assertThat(loaded.name()).isEqualTo("Algodón Pima");
        assertThat(loaded.materialType()).isEqualTo("Fibra");
        assertThat(loaded.quantity()).isEqualTo(Quantity.ofKg("250.5"));
        assertThat(loaded.composition()).isEqualTo(composition("Algodón", "70", "Poliéster", "30"));
        assertThat(loaded.phase()).isEqualTo(BatchPhase.AVAILABLE);
        assertThat(loaded.qualityStatus()).isEqualTo(QualityStatus.NOT_REVIEWED);
        assertThat(loaded.qualityHold()).isFalse();
        assertThat(loaded.isFinalProduct()).isFalse();
        assertThat(loaded.originType()).isEqualTo(BatchOriginType.REGISTERED);
        assertThat(loaded.origin()).isEqualTo("Piura");
        assertThat(loaded.supplierName()).isEqualTo("Textiles del Norte");
        assertThat(loaded.receptionCharacteristics()).isEqualTo("Fibra limpia");
        assertThat(loaded.createdBy()).isEqualTo(OPERATOR);
        assertThat(loaded.createdAt()).isEqualTo(START);
        assertThat(loaded.commercialData().isEmpty()).isTrue();
        assertThat(loaded.version()).isZero();
    }

    @Test
    void theCompositionKeepsItsOrder() {
        Batch saved = savedBatch(composition("Zeta", "10", "Alfa", "20.5", "Media", "69.5"), "10");

        Batch loaded = inTx(() -> batches.findById(saved.id()).orElseThrow());

        assertThat(loaded.composition().components()).extracting(c -> c.name()).containsExactly("Zeta", "Alfa", "Media");
        assertThat(loaded.composition().components()).extracting(c -> c.percentage().toPlainString())
                .containsExactly("10.00", "20.50", "69.50");
    }

    @Test
    void anUnknownSupplierIsStoredAsNull() {
        Batch saved = savedBatch(composition("Algodón", "100"), "10");
        assertThat(inTx(() -> batches.findById(saved.id()).orElseThrow()).supplierName()).isNull();
    }

    @Test
    void splitChildrenAndTheirParentArePersisted() {
        Batch parent = savedBatch("250.5");

        BatchFactory.SplitResult result = inTx(() -> {
            Batch loaded = batches.findByIdForUpdate(parent.id()).orElseThrow();
            BatchFactory.SplitResult split = batchFactory.split(loaded, List.of(
                    new BatchFactory.ChildSpec("Hijo 1", Quantity.ofKg("100.250")),
                    new BatchFactory.ChildSpec(null, Quantity.ofKg("150.250"))), OPERATOR, START.plusSeconds(60));
            batches.saveAll(split.children());
            batches.save(loaded);
            return split;
        });

        Batch reloadedParent = inTx(() -> batches.findById(parent.id()).orElseThrow());
        assertThat(reloadedParent.phase()).isEqualTo(BatchPhase.SPLIT);
        assertThat(reloadedParent.version()).isEqualTo(1);
        for (Batch child : result.children()) {
            Batch reloaded = inTx(() -> batches.findById(child.id()).orElseThrow());
            assertThat(reloaded.originType()).isEqualTo(BatchOriginType.SPLIT);
            assertThat(reloaded.composition()).isEqualTo(parent.composition());
            assertThat(reloaded.origin()).isEqualTo("Piura");
            assertThat(reloaded.supplierName()).isEqualTo("Textiles del Norte");
            assertThat(reloaded.traceabilityId()).isEqualTo(child.traceabilityId());
            assertThat(reloaded.createdAt()).isEqualTo(START.plusSeconds(60));
        }
        assertThat(inTx(() -> batches.findById(result.children().get(0).id()).orElseThrow()).name()).isEqualTo("Hijo 1");
        assertThat(inTx(() -> batches.findById(result.children().get(1).id()).orElseThrow()).name()).isEqualTo("Algodón Pima");
    }

    @Test
    void aFinalProductWithCommercialDataRoundTrips() {
        CommercialData data = new CommercialData("Tiendas SAC", new BigDecimal("1250.5"), "PEN", LocalDate.parse("2026-11-03"), "OC-77");
        Batch input = savedBatch("100");
        Batch output = inTx(() -> {
            Batch loaded = batches.findByIdForUpdate(input.id()).orElseThrow();
            var machine = com.nodotextil.trazatex.production.domain.model.Machine.register(UUID.randomUUID(), COMPANY, "M1", "M", "T", START);
            machines.save(machine);
            var transformation = transformationFactory.start(UUID.randomUUID(),
                    com.nodotextil.trazatex.production.domain.model.TransformationType.DYEING, COMPANY, machine, OPERATOR, List.of(loaded), START);
            List<Batch> produced = transformation.finalize(List.of(loaded), List.of(new BatchFactory.OutputSpec("Tela", "Tela",
                    Quantity.ofKg("90"), true, data)), null, null, batchFactory, OPERATOR, START);
            batches.save(loaded);
            batches.saveAll(produced);
            transformations.save(transformation);
            return produced.get(0);
        });

        Batch loaded = inTx(() -> batches.findById(output.id()).orElseThrow());

        assertThat(loaded.isFinalProduct()).isTrue();
        assertThat(loaded.originType()).isEqualTo(BatchOriginType.TRANSFORMATION);
        assertThat(loaded.origin()).isNull();
        assertThat(loaded.receptionCharacteristics()).isNull();
        assertThat(loaded.commercialData()).isEqualTo(data);
        assertThat(loaded.commercialData().salePrice()).isEqualByComparingTo("1250.50");
        assertThat(loaded.composition()).isEqualTo(input.composition());
    }

    // ---- actualizaciones y versión ----

    @Test
    void updatesChangeTheMutableFieldsAndBumpTheVersionEachTime() {
        Batch saved = savedBatch("10");

        inTx(() -> {
            Batch batch = batches.findByIdForUpdate(saved.id()).orElseThrow();
            batch.startEvaluation();
            batch.showQualityStatus(QualityStatus.PENDING_REEVALUATION);
            batches.save(batch);
        });
        Batch afterFirst = inTx(() -> batches.findById(saved.id()).orElseThrow());
        assertThat(afterFirst.phase()).isEqualTo(BatchPhase.IN_QUALITY_CONTROL);
        assertThat(afterFirst.qualityStatus()).isEqualTo(QualityStatus.PENDING_REEVALUATION);
        assertThat(afterFirst.version()).isEqualTo(1);

        inTx(() -> {
            Batch batch = batches.findByIdForUpdate(saved.id()).orElseThrow();
            batch.release(true);
            batches.save(batch);
        });
        Batch afterSecond = inTx(() -> batches.findById(saved.id()).orElseThrow());
        assertThat(afterSecond.phase()).isEqualTo(BatchPhase.AVAILABLE);
        assertThat(afterSecond.qualityHold()).isTrue();
        assertThat(afterSecond.version()).isEqualTo(2);

        // lo que nunca cambia
        assertThat(afterSecond.traceabilityId()).isEqualTo(saved.traceabilityId());
        assertThat(afterSecond.qrCode()).isEqualTo(saved.qrCode());
        assertThat(afterSecond.quantity()).isEqualTo(saved.quantity());
        assertThat(afterSecond.composition()).isEqualTo(saved.composition());
        assertThat(afterSecond.createdAt()).isEqualTo(saved.createdAt());
    }

    @Test
    void savingWithoutChangesDoesNotBumpTheVersion() {
        Batch saved = savedBatch("10");

        inTx(() -> batches.save(batches.findByIdForUpdate(saved.id()).orElseThrow()));

        assertThat(inTx(() -> batches.findById(saved.id()).orElseThrow()).version()).isZero();
    }

    @Test
    void acceptingATransferChangesTheCompanyAndKeepsTheCodes() {
        Batch saved = savedBatch("10");
        inTx(() -> {
            Batch batch = batches.findByIdForUpdate(saved.id()).orElseThrow();
            batch.startTransfer();
            batches.save(batch);
        });
        inTx(() -> {
            Batch batch = batches.findByIdForUpdate(saved.id()).orElseThrow();
            batch.acceptTransfer(OTHER_COMPANY);
            batches.save(batch);
        });

        Batch loaded = inTx(() -> batches.findById(saved.id()).orElseThrow());

        assertThat(loaded.companyId()).isEqualTo(OTHER_COMPANY);
        assertThat(loaded.phase()).isEqualTo(BatchPhase.AVAILABLE);
        assertThat(loaded.traceabilityId()).isEqualTo(saved.traceabilityId());
        assertThat(loaded.qrCode()).isEqualTo(saved.qrCode());
        assertThat(loaded.version()).isEqualTo(2);
    }

    @Test
    void commercialDataEditsArePersisted() {
        Batch saved = savedBatch("10");
        // un producto final solo nace de una transformación: se arma por SQL para aislar la actualización
        jdbc.sql("UPDATE batches SET final_product = TRUE WHERE id = :id").param("id", saved.id()).update();
        CommercialData data = new CommercialData("Tiendas SAC", new BigDecimal("99.90"), "USD", null, null);

        inTx(() -> {
            Batch batch = batches.findByIdForUpdate(saved.id()).orElseThrow();
            batch.updateCommercialData(data);
            batches.save(batch);
        });

        assertThat(inTx(() -> batches.findById(saved.id()).orElseThrow()).commercialData()).isEqualTo(data);
    }

    // ---- bloqueo optimista ----

    @Test
    void aStaleWriteIsRejectedByOptimisticLocking() throws Exception {
        Batch saved = savedBatch("10");
        CountDownLatch loadedByBoth = new CountDownLatch(2);
        CountDownLatch otherCommitted = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> slow = pool.submit(() -> {
                try {
                    return inTx(() -> {
                        Batch batch = batches.findById(saved.id()).orElseThrow();
                        loadedByBoth.countDown();
                        await(otherCommitted);
                        batch.hold();
                        batches.save(batch);
                        return "committed";
                    });
                } catch (Exception e) {
                    return e;
                }
            });
            Future<Object> fast = pool.submit(() -> {
                try {
                    return inTx(() -> {
                        Batch batch = batches.findById(saved.id()).orElseThrow();
                        loadedByBoth.countDown();
                        await(loadedByBoth);
                        batch.startEvaluation();
                        batches.save(batch);
                        return "committed";
                    });
                } finally {
                    otherCommitted.countDown();
                }
            });

            assertThat(fast.get(10, TimeUnit.SECONDS)).isEqualTo("committed");
            assertThat(slow.get(10, TimeUnit.SECONDS)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        } finally {
            pool.shutdownNow();
        }
        Batch loaded = inTx(() -> batches.findById(saved.id()).orElseThrow());
        assertThat(loaded.phase()).isEqualTo(BatchPhase.IN_QUALITY_CONTROL);
        assertThat(loaded.qualityHold()).as("la escritura perdedora no dejó rastro").isFalse();
        assertThat(loaded.version()).isEqualTo(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("la otra transacción no avanzó");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- consultas ----

    @Test
    void findsByQrCodeAndReturnsNothingForAnUnknownOne() {
        Batch saved = savedBatch("10");

        assertThat(inTx(() -> batches.findByQrCode(saved.qrCode()))).get().extracting(Batch::id).isEqualTo(saved.id());
        assertThat(inTx(() -> batches.findByQrCode(QrCode.generate(new java.util.Random(7))))).isEmpty();
        assertThat(inTx(() -> batches.findById(UUID.randomUUID()))).isEmpty();
        assertThat(inTx(() -> batches.findByIdForUpdate(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void codeTakenKnowsBothTheTraceabilityIdAndTheQr() {
        Batch saved = savedBatch("10");

        assertThat(batches.isCodeTaken(saved.traceabilityId().value())).isTrue();
        assertThat(batches.isCodeTaken(saved.qrCode().value())).isTrue();
        assertThat(batches.isCodeTaken(TraceabilityId.generate(new java.util.Random(1)).value())).isFalse();
        assertThat(batches.isCodeTaken("TT-0000000000")).isFalse();
    }

    @Test
    void lockedReadsReturnTheBatchesInIdOrderAndSkipTheMissingOnes() {
        List<Batch> saved = java.util.stream.IntStream.range(0, 6).mapToObj(i -> savedBatch("1" + i)).toList();
        List<UUID> shuffled = new java.util.ArrayList<>(saved.stream().map(Batch::id).toList());
        java.util.Collections.shuffle(shuffled, new java.util.Random(3));
        shuffled.add(UUID.randomUUID());

        List<Batch> locked = inTx(() -> batches.findAllForUpdate(shuffled));

        assertThat(locked).hasSize(6);
        // PostgreSQL ordena los uuid por bytes sin signo; UUID.compareTo de Java compara con signo, así que se compara el texto
        assertThat(locked).extracting(batch -> batch.id().toString()).isSorted();
        assertThat(inTx(() -> batches.findAllForUpdate(List.of()))).isEmpty();
    }

    // ---- búsqueda ----

    private Batch savedWith(UUID company, String material, Instant createdAt, java.util.function.Consumer<Batch> change) {
        Batch batch = inTx(() -> {
            Batch created = batchFactory.register(new BatchFactory.Registration(null, material,
                    composition("Algodón", "100"), Quantity.ofKg("10"), "Piura", null, "ok"), company, OPERATOR, createdAt);
            batches.save(created);
            return created;
        });
        if (change != null) {
            inTx(() -> {
                Batch loaded = batches.findByIdForUpdate(batch.id()).orElseThrow();
                change.accept(loaded);
                batches.save(loaded);
            });
        }
        return batch;
    }

    private static BatchSearchCriteria criteria(UUID company) {
        return new BatchSearchCriteria(company, null, null, null, null, null, null, 0, 50);
    }

    @Test
    void searchOnlyReturnsTheBatchesOfTheCompanyNewestFirst() {
        Batch oldest = savedWith(COMPANY, "Fibra", START.minus(Duration.ofDays(3)), null);
        Batch newest = savedWith(COMPANY, "Fibra", START.minus(Duration.ofDays(1)), null);
        Batch middle = savedWith(COMPANY, "Fibra", START.minus(Duration.ofDays(2)), null);
        savedWith(OTHER_COMPANY, "Fibra", START, null);

        Page<Batch> page = inTx(() -> batches.search(criteria(COMPANY)));

        assertThat(page.items()).extracting(Batch::id).containsExactly(newest.id(), middle.id(), oldest.id());
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(inTx(() -> batches.search(criteria(UUID.randomUUID()))).items()).isEmpty();
    }

    @Test
    void searchFiltersByPhaseQualityMaterialAndDates() {
        Batch fiber = savedWith(COMPANY, "Fibra", START.minus(Duration.ofDays(5)), null);
        Batch yarn = savedWith(COMPANY, "Hilo", START.minus(Duration.ofDays(3)), null);
        Batch inQuality = savedWith(COMPANY, "Hilo", START.minus(Duration.ofDays(1)), Batch::startEvaluation);
        Batch failed = savedWith(COMPANY, "Tela", START, b -> b.showQualityStatus(QualityStatus.FAILED));

        assertThat(search(new BatchSearchCriteria(COMPANY, null, "hilo", null, null, null, null, 0, 50)))
                .containsExactlyInAnyOrder(yarn.id(), inQuality.id());
        assertThat(search(new BatchSearchCriteria(COMPANY, null, " FIBRA ", null, null, null, null, 0, 50))).containsExactly(fiber.id());
        assertThat(search(new BatchSearchCriteria(COMPANY, null, null, BatchPhase.IN_QUALITY_CONTROL, null, null, null, 0, 50)))
                .containsExactly(inQuality.id());
        assertThat(search(new BatchSearchCriteria(COMPANY, null, null, BatchPhase.SPLIT, null, null, null, 0, 50))).isEmpty();
        assertThat(search(new BatchSearchCriteria(COMPANY, null, null, null, QualityStatus.FAILED, null, null, 0, 50))).containsExactly(failed.id());
        // [desde, hasta): el límite superior no entra
        assertThat(search(new BatchSearchCriteria(COMPANY, null, null, null, null, START.minus(Duration.ofDays(3)), START.minus(Duration.ofDays(1)), 0, 50)))
                .containsExactly(yarn.id());
        assertThat(search(new BatchSearchCriteria(COMPANY, null, null, null, null, START.minus(Duration.ofDays(1)), null, 0, 50)))
                .containsExactly(failed.id(), inQuality.id());
        assertThat(search(new BatchSearchCriteria(COMPANY, null, null, null, null, null, START.minus(Duration.ofDays(5)), 0, 50))).isEmpty();
        // varios filtros a la vez
        assertThat(search(new BatchSearchCriteria(COMPANY, null, "Hilo", BatchPhase.AVAILABLE, QualityStatus.NOT_REVIEWED, null, null, 0, 50)))
                .containsExactly(yarn.id());
    }

    @Test
    void searchByTraceabilityIdPrefixMatchesTheStartOnlyAndIsSafeAgainstWildcards() {
        Batch batch = savedWith(COMPANY, "Fibra", START, null);
        savedWith(COMPANY, "Fibra", START.plusSeconds(1), null);
        String id = batch.traceabilityId().value();

        assertThat(search(new BatchSearchCriteria(COMPANY, id, null, null, null, null, null, 0, 50))).containsExactly(batch.id());
        assertThat(search(new BatchSearchCriteria(COMPANY, id.substring(0, 5), null, null, null, null, null, 0, 50))).contains(batch.id());
        assertThat(search(new BatchSearchCriteria(COMPANY, "TT-", null, null, null, null, null, 0, 50))).hasSize(2);
        assertThat(search(new BatchSearchCriteria(COMPANY, id.substring(3), null, null, null, null, null, 0, 50))).as("no es 'contiene'").isEmpty();
        assertThat(search(new BatchSearchCriteria(COMPANY, "%", null, null, null, null, null, 0, 50))).isEmpty();
        assertThat(search(new BatchSearchCriteria(COMPANY, "TT-_", null, null, null, null, null, 0, 50))).isEmpty();
        assertThat(search(new BatchSearchCriteria(COMPANY, "\\", null, null, null, null, null, 0, 50))).isEmpty();
        assertThat(search(new BatchSearchCriteria(COMPANY, "", null, null, null, null, null, 0, 50))).hasSize(2);
    }

    @Test
    void searchPaginatesWithAStableOrder() {
        List<Batch> created = java.util.stream.IntStream.range(0, 7)
                .mapToObj(i -> savedWith(COMPANY, "Fibra", START.minusSeconds(i), null)).toList();

        Page<Batch> first = inTx(() -> batches.search(new BatchSearchCriteria(COMPANY, null, null, null, null, null, null, 0, 3)));
        Page<Batch> second = inTx(() -> batches.search(new BatchSearchCriteria(COMPANY, null, null, null, null, null, null, 1, 3)));
        Page<Batch> third = inTx(() -> batches.search(new BatchSearchCriteria(COMPANY, null, null, null, null, null, null, 2, 3)));
        Page<Batch> beyond = inTx(() -> batches.search(new BatchSearchCriteria(COMPANY, null, null, null, null, null, null, 3, 3)));

        assertThat(first.totalElements()).isEqualTo(7);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.items()).hasSize(3);
        assertThat(second.items()).hasSize(3);
        assertThat(third.items()).hasSize(1);
        assertThat(beyond.items()).isEmpty();
        List<UUID> all = java.util.stream.Stream.of(first, second, third).flatMap(p -> p.items().stream()).map(Batch::id).toList();
        assertThat(all).containsExactlyElementsOf(created.stream().map(Batch::id).toList());
    }

    @Test
    void batchesWithTheSameCreationTimeKeepAStableOrder() {
        List<Batch> same = java.util.stream.IntStream.range(0, 5).mapToObj(i -> savedWith(COMPANY, "Fibra", START, null)).toList();

        List<UUID> twice = search(criteria(COMPANY));
        assertThat(twice).isEqualTo(search(criteria(COMPANY)));
        assertThat(twice).containsExactlyInAnyOrderElementsOf(same.stream().map(Batch::id).toList());
    }

    private List<UUID> search(BatchSearchCriteria criteria) {
        return inTx(() -> batches.search(criteria)).items().stream().map(Batch::id).toList();
    }
}
