package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.nodotextil.trazatex.production.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.NOW;
import static com.nodotextil.trazatex.production.domain.Fixtures.OPERATOR;
import static com.nodotextil.trazatex.production.domain.Fixtures.composition;
import static com.nodotextil.trazatex.production.domain.Fixtures.cotton;
import static com.nodotextil.trazatex.production.domain.Fixtures.factory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Registro (US12) y división (US15). */
class BatchFactoryTest {

    /** Semilla fija: los casos son aleatorios pero idénticos en cada corrida, así que cualquier fallo se reproduce. */
    private static final long SEED = 20261020L;
    private static final int CASES = 1500;

    private static BatchFactory.Registration registration() {
        return new BatchFactory.Registration("Algodón Pima", "Fibra", composition("Algodón", "70", "Poliéster", "30"),
                Quantity.ofKg("250.5"), "Piura", "Textiles del Norte", "Fibra limpia, humedad 8 %");
    }

    // --- registro (US12) ---------------------------------------------------------------------------------------

    @Test
    void registersAnAvailableNotReviewedBatchWithNewCodes() {
        Batch batch = factory(1).register(registration(), COMPANY, OPERATOR, NOW);

        assertThat(batch.phase()).isEqualTo(BatchPhase.AVAILABLE);
        assertThat(batch.qualityStatus()).isEqualTo(QualityStatus.NOT_REVIEWED);
        assertThat(batch.qualityHold()).isFalse();
        assertThat(batch.isFinalProduct()).isFalse();
        assertThat(batch.originType()).isEqualTo(BatchOriginType.REGISTERED);
        assertThat(batch.companyId()).isEqualTo(COMPANY);
        assertThat(batch.createdBy()).isEqualTo(OPERATOR);
        assertThat(batch.createdAt()).isEqualTo(NOW);
        assertThat(batch.traceabilityId().value()).startsWith("TT-");
        assertThat(batch.qrCode().value()).hasSize(QrCode.LENGTH);
        assertThat(batch.qrCode().value()).isNotEqualTo(batch.traceabilityId().value());
        assertThat(batch.isEnabled()).isTrue();
        assertThat(batch.quantity()).isEqualTo(Quantity.ofKg("250.5"));
        assertThat(batch.version()).isZero();
    }

    @Test
    void theSupplierIsOptional() {
        BatchFactory.Registration unknownSupplier = new BatchFactory.Registration(null, "Fibra", cotton(),
                Quantity.ofKg("10"), "Piura", "  ", "Fibra limpia");

        Batch batch = factory(1).register(unknownSupplier, COMPANY, OPERATOR, NOW);

        assertThat(batch.supplierName()).isNull();
        assertThat(batch.name()).as("sin nombre se usa el Traceability ID").isEqualTo(batch.traceabilityId().value());
    }

    @Test
    void reportsEveryMissingRequiredField() {
        BatchFactory.Registration empty = new BatchFactory.Registration("x", " ", null, null, "", null, "  ");

        assertThatThrownBy(() -> factory(1).register(empty, COMPANY, OPERATOR, NOW))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCodes.MISSING_FIELDS);
                    assertThat(e.details()).containsExactlyInAnyOrder("origin", "materialType", "composition", "quantity", "receptionCharacteristics");
                });
    }

    @Test
    void everyBatchGetsDifferentCodesAndIds() {
        BatchFactory factory = factory(7);
        Set<String> codes = new HashSet<>();
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            Batch batch = factory.register(registration(), COMPANY, OPERATOR, NOW);
            assertThat(codes.add(batch.traceabilityId().value())).isTrue();
            assertThat(codes.add(batch.qrCode().value())).isTrue();
            assertThat(ids.add(batch.id())).isTrue();
        }
    }

    @Test
    void secureFactoryWorks() {
        Batch batch = BatchFactory.secure().register(registration(), COMPANY, OPERATOR, NOW);
        assertThat(batch.traceabilityId().value()).hasSize(13);
    }

    // --- unicidad de los códigos ------------------------------------------------------------------------------

    @Test
    void whenACodeIsTakenTheFactoryDrawsAnotherOne() {
        // el primer Traceability ID que sorteará esta semilla ya "existe" en la base
        String alreadyUsed = TraceabilityId.generate(new Random(99)).value();
        BatchFactory factory = new BatchFactory(new Random(99), UUID::randomUUID, alreadyUsed::equals);

        Batch batch = factory.register(registration(), COMPANY, OPERATOR, NOW);

        assertThat(batch.traceabilityId().value()).isNotEqualTo(alreadyUsed).startsWith("TT-");
    }

    @Test
    void everyCodeAssignedIsCheckedAgainstThePredicateIncludingChildrenAndOutputs() {
        Set<String> checked = new HashSet<>();
        BatchFactory factory = new BatchFactory(new Random(5), UUID::randomUUID, code -> {
            checked.add(code);
            return false;
        });
        Batch parent = factory.register(registration(), COMPANY, OPERATOR, NOW);
        BatchFactory.SplitResult split = factory.split(parent, List.of(
                new BatchFactory.ChildSpec(null, Quantity.ofKg("100")),
                new BatchFactory.ChildSpec(null, Quantity.ofKg("150.5"))), OPERATOR, NOW);

        assertThat(checked).contains(parent.traceabilityId().value(), parent.qrCode().value());
        split.children().forEach(child -> assertThat(checked).contains(child.traceabilityId().value(), child.qrCode().value()));
    }

    @Test
    void failsClearlyWhenNoFreeCodeIsFound() {
        BatchFactory factory = new BatchFactory(new Random(5), UUID::randomUUID, code -> true);

        assertThatThrownBy(() -> factory.register(registration(), COMPANY, OPERATOR, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Traceability ID libre");
    }

    // --- división (US15) ----------------------------------------------------------------------------------------

    @Test
    void splitsIntoChildrenThatInheritTheParentData() {
        BatchFactory factory = factory(3);
        Batch parent = factory.register(registration(), COMPANY, OPERATOR, NOW);
        parent.showQualityStatus(QualityStatus.CONFORMING);
        UUID splitter = UUID.randomUUID();

        BatchFactory.SplitResult result = factory.split(parent, List.of(
                new BatchFactory.ChildSpec("Hijo A", Quantity.ofKg("100.5")),
                new BatchFactory.ChildSpec(null, Quantity.ofKg("150"))), splitter, NOW.plusSeconds(60));

        assertThat(result.parent()).isSameAs(parent);
        assertThat(parent.phase()).isEqualTo(BatchPhase.SPLIT);
        assertThat(result.children()).hasSize(2);
        for (Batch child : result.children()) {
            assertThat(child.phase()).isEqualTo(BatchPhase.AVAILABLE);
            assertThat(child.originType()).isEqualTo(BatchOriginType.SPLIT);
            assertThat(child.companyId()).isEqualTo(COMPANY);
            assertThat(child.composition()).isEqualTo(parent.composition());
            assertThat(child.materialType()).isEqualTo(parent.materialType());
            assertThat(child.origin()).isEqualTo(parent.origin());
            assertThat(child.supplierName()).isEqualTo(parent.supplierName());
            assertThat(child.receptionCharacteristics()).isEqualTo(parent.receptionCharacteristics());
            assertThat(child.qualityStatus()).as("decisión 1: el hijo hereda el estado de calidad del padre").isEqualTo(QualityStatus.CONFORMING);
            assertThat(child.qualityHold()).isFalse();
            assertThat(child.isFinalProduct()).isFalse();
            assertThat(child.createdBy()).isEqualTo(splitter);
            assertThat(child.createdAt()).isEqualTo(NOW.plusSeconds(60));
            assertThat(child.traceabilityId()).isNotEqualTo(parent.traceabilityId());
            assertThat(child.qrCode()).isNotEqualTo(parent.qrCode());
        }
        assertThat(result.children().get(0).name()).isEqualTo("Hijo A");
        assertThat(result.children().get(1).name()).as("sin nombre, el del padre").isEqualTo("Algodón Pima");
        assertThat(result.children().get(0).traceabilityId()).isNotEqualTo(result.children().get(1).traceabilityId());
    }

    @Test
    void childrenInheritAHeldQualityStatusOnlyAsACopy() {
        BatchFactory factory = factory(3);
        Batch parent = factory.register(registration(), COMPANY, OPERATOR, NOW);
        parent.showQualityStatus(QualityStatus.FAILED);   // un FAILED ya liberado: la retención (hold) es false

        BatchFactory.SplitResult result = factory.split(parent, List.of(
                new BatchFactory.ChildSpec(null, Quantity.ofKg("1")),
                new BatchFactory.ChildSpec(null, Quantity.ofKg("249.5"))), OPERATOR, NOW);

        assertThat(result.children()).allSatisfy(child -> {
            assertThat(child.qualityStatus()).isEqualTo(QualityStatus.FAILED);
            assertThat(child.qualityHold()).isFalse();
        });
    }

    @Test
    void aSingleChildIsNotADivision() {
        BatchFactory factory = factory(3);
        Batch parent = factory.register(registration(), COMPANY, OPERATOR, NOW);

        assertThatThrownBy(() -> factory.split(parent, List.of(new BatchFactory.ChildSpec(null, Quantity.ofKg("250.5"))), OPERATOR, NOW))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.SPLIT_REQUIRES_TWO_CHILDREN));
        assertThatThrownBy(() -> factory.split(parent, List.of(), OPERATOR, NOW)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> factory.split(parent, null, OPERATOR, NOW)).isInstanceOf(ValidationException.class);
        assertThat(parent.phase()).isEqualTo(BatchPhase.AVAILABLE);
    }

    @Test
    void mismatchedQuantitiesAreRejectedWithAClearMessage() {
        BatchFactory factory = factory(3);
        Batch parent = factory.register(registration(), COMPANY, OPERATOR, NOW);

        assertThatThrownBy(() -> factory.split(parent, List.of(
                new BatchFactory.ChildSpec(null, Quantity.ofKg("100")),
                new BatchFactory.ChildSpec(null, Quantity.ofKg("150"))), OPERATOR, NOW))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCodes.SPLIT_QUANTITIES_MISMATCH);
                    assertThat(e.getMessage()).contains("Las cantidades no coinciden").contains("250.000 kg").contains("250.500 kg");
                });
        assertThat(parent.phase()).isEqualTo(BatchPhase.AVAILABLE);
    }

    @Test
    void anUnavailableOrHeldParentCannotBeSplit() {
        BatchFactory factory = factory(3);
        Batch held = factory.register(registration(), COMPANY, OPERATOR, NOW);
        held.hold();

        assertThatThrownBy(() -> factory.split(held, List.of(
                new BatchFactory.ChildSpec(null, Quantity.ofKg("100")),
                new BatchFactory.ChildSpec(null, Quantity.ofKg("150.5"))), OPERATOR, NOW))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.BATCH_ON_QUALITY_HOLD));
        assertThat(held.phase()).isEqualTo(BatchPhase.AVAILABLE);
    }

    /** Propiedad (≥ 1000 casos): una división cuyos hijos suman exactamente al padre se acepta y conserva la masa. */
    @ParameterizedTest(name = "caso {index}")
    @MethodSource("exactPartitions")
    void aDivisionThatAddsUpExactlyConservesMassAndInvariants(long parentGrams, List<Long> childGrams, QualityStatus status) {
        BatchFactory factory = factory(parentGrams);
        Batch parent = Batch.restore(new BatchSnapshot(UUID.randomUUID(), TraceabilityId.generate(new Random(parentGrams)),
                QrCode.generate(new Random(parentGrams)), COMPANY, "P", "Fibra", grams(parentGrams),
                composition("Algodón", "60", "Lana", "40"), BatchPhase.AVAILABLE, status, false, false,
                BatchOriginType.REGISTERED, "Piura", null, "ok", OPERATOR, NOW, CommercialData.EMPTY, 0));

        BatchFactory.SplitResult result = factory.split(parent, specs(childGrams), OPERATOR, NOW);

        assertThat(parent.phase()).isEqualTo(BatchPhase.SPLIT);
        assertThat(result.children()).hasSameSizeAs(childGrams);
        Quantity total = Quantity.ZERO;
        Set<String> codes = new HashSet<>();
        codes.add(parent.traceabilityId().value());
        codes.add(parent.qrCode().value());
        for (int i = 0; i < childGrams.size(); i++) {
            Batch child = result.children().get(i);
            assertThat(child.quantity()).isEqualTo(grams(childGrams.get(i)));
            assertThat(child.phase()).isEqualTo(BatchPhase.AVAILABLE);
            assertThat(child.composition()).isEqualTo(parent.composition());
            assertThat(child.qualityStatus()).isEqualTo(status);
            assertThat(codes.add(child.traceabilityId().value())).as("Traceability ID nuevo").isTrue();
            assertThat(codes.add(child.qrCode().value())).as("QR nuevo").isTrue();
            total = total.plus(child.quantity());
        }
        assertThat(total).as("la masa se conserva exactamente").isEqualTo(parent.quantity());
    }

    /** Propiedad (≥ 1000 casos): si la suma difiere del padre aunque sea en 1 g, se rechaza y nada cambia. */
    @ParameterizedTest(name = "caso {index}")
    @MethodSource("inexactPartitions")
    void aDivisionThatDoesNotAddUpIsRejectedAndChangesNothing(long parentGrams, List<Long> childGrams) {
        BatchFactory factory = factory(parentGrams);
        Batch parent = Batch.restore(new BatchSnapshot(UUID.randomUUID(), TraceabilityId.generate(new Random(parentGrams)),
                QrCode.generate(new Random(parentGrams)), COMPANY, "P", "Fibra", grams(parentGrams), cotton(),
                BatchPhase.AVAILABLE, QualityStatus.NOT_REVIEWED, false, false, BatchOriginType.REGISTERED, "Piura",
                null, "ok", OPERATOR, NOW, CommercialData.EMPTY, 0));

        assertThatThrownBy(() -> factory.split(parent, specs(childGrams), OPERATOR, NOW))
                .isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.SPLIT_QUANTITIES_MISMATCH));
        assertThat(parent.phase()).isEqualTo(BatchPhase.AVAILABLE);
    }

    private static Quantity grams(long grams) {
        return Quantity.positive(BigDecimal.valueOf(grams, 3));
    }

    private static List<BatchFactory.ChildSpec> specs(List<Long> childGrams) {
        return childGrams.stream().map(g -> new BatchFactory.ChildSpec(null, grams(g))).toList();
    }

    /** Reparte {@code total} gramos en {@code parts} partes positivas con {@code parts − 1} cortes distintos. */
    private static List<Long> partition(Random random, long total, int parts) {
        TreeSet<Long> cuts = new TreeSet<>();
        while (cuts.size() < parts - 1) {
            cuts.add(1 + (long) (random.nextDouble() * (total - 1)));
        }
        List<Long> result = new ArrayList<>();
        long previous = 0;
        for (long cut : cuts) {
            result.add(cut - previous);
            previous = cut;
        }
        result.add(total - previous);
        return result;
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> exactPartitions() {
        Random random = new Random(SEED);
        QualityStatus[] statuses = QualityStatus.values();
        return IntStream.range(0, CASES).mapToObj(i -> {
            int parts = 2 + random.nextInt(6);
            long total = parts + random.nextInt(5_000_000);
            if (i % 10 == 0) {
                total = parts;      // el caso límite: todos los hijos de 1 g
            }
            return org.junit.jupiter.params.provider.Arguments.of(total, partition(random, total, parts),
                    statuses[random.nextInt(statuses.length)]);
        });
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> inexactPartitions() {
        Random random = new Random(SEED + 1);
        return IntStream.range(0, CASES).mapToObj(i -> {
            int parts = 2 + random.nextInt(5);
            long total = parts + random.nextInt(5_000_000);
            List<Long> children = new ArrayList<>(partition(random, total, parts));
            int victim = random.nextInt(parts);
            long delta = random.nextBoolean() ? 1 : -1;
            if (delta < 0 && children.get(victim) == 1) {
                delta = 1;   // una parte no puede quedar en 0
            }
            if (random.nextInt(4) == 0) {
                delta = delta * (1 + random.nextInt(1000));
                if (children.get(victim) + delta <= 0) {
                    delta = 1;
                }
            }
            children.set(victim, children.get(victim) + delta);
            return org.junit.jupiter.params.provider.Arguments.of(total, children);
        });
    }
}
