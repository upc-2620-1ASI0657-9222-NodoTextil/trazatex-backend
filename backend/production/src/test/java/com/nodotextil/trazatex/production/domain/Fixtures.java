package com.nodotextil.trazatex.production.domain;

import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.BatchFactory;
import com.nodotextil.trazatex.production.domain.model.BatchOriginType;
import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.BatchSnapshot;
import com.nodotextil.trazatex.production.domain.model.CommercialData;
import com.nodotextil.trazatex.production.domain.model.Composition;
import com.nodotextil.trazatex.production.domain.model.CompositionComponent;
import com.nodotextil.trazatex.production.domain.model.Machine;
import com.nodotextil.trazatex.production.domain.model.QrCode;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;
import com.nodotextil.trazatex.production.domain.model.Quantity;
import com.nodotextil.trazatex.production.domain.model.TraceabilityId;
import com.nodotextil.trazatex.production.domain.service.DyeingStrategy;
import com.nodotextil.trazatex.production.domain.service.SpinningStrategy;
import com.nodotextil.trazatex.production.domain.service.TransformationFactory;
import com.nodotextil.trazatex.production.domain.service.WeavingStrategy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Datos de prueba. Todo lo aleatorio sale de un {@link Random} con semilla: cualquier fallo se reproduce. */
public final class Fixtures {

    public static final UUID COMPANY = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    public static final UUID OTHER_COMPANY = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    public static final UUID OPERATOR = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    public static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    private Fixtures() {
    }

    public static UUID uuid(Random random) {
        return new UUID(random.nextLong(), random.nextLong());
    }

    public static BatchFactory factory(long seed) {
        Random random = new Random(seed);
        return new BatchFactory(random, () -> uuid(random));
    }

    /** Estrategias con los mínimos por defecto: 60 % hilandería, 80 % tejeduría y tintorería. */
    public static TransformationFactory transformationFactory() {
        return new TransformationFactory(List.of(
                new SpinningStrategy(new BigDecimal("60")),
                new WeavingStrategy(new BigDecimal("80")),
                new DyeingStrategy(new BigDecimal("80"))));
    }

    public static Composition composition(String... namesAndPercentages) {
        List<CompositionComponent> components = new ArrayList<>();
        for (int i = 0; i < namesAndPercentages.length; i += 2) {
            components.add(CompositionComponent.of(namesAndPercentages[i], namesAndPercentages[i + 1]));
        }
        return Composition.of(components);
    }

    public static Composition cotton() {
        return composition("Algodón", "100");
    }

    /** Lote de {@link #COMPANY} en la fase y con las marcas dadas (los lotes se crean con {@link BatchFactory}; esto reconstruye). */
    public static Batch batch(BatchPhase phase, boolean qualityHold, boolean finalProduct, String kg) {
        return batch(phase, qualityHold, finalProduct, Quantity.ofKg(kg), cotton(), UUID.randomUUID(), QualityStatus.NOT_REVIEWED);
    }

    public static Batch batch(BatchPhase phase, boolean qualityHold, boolean finalProduct, Quantity quantity,
                              Composition composition, UUID id, QualityStatus qualityStatus) {
        Random random = new Random(id.getLeastSignificantBits());
        return Batch.restore(new BatchSnapshot(id, TraceabilityId.generate(random), QrCode.generate(random), COMPANY,
                "Lote de prueba", "Fibra", quantity, composition, phase, qualityStatus, qualityHold, finalProduct,
                BatchOriginType.REGISTERED, "Piura", "Proveedor", "Fibra limpia", OPERATOR, NOW, CommercialData.EMPTY, 0));
    }

    public static Batch availableBatch(String kg) {
        return batch(BatchPhase.AVAILABLE, false, false, kg);
    }

    public static Batch availableBatch(Quantity quantity, Composition composition, Random random) {
        return batch(BatchPhase.AVAILABLE, false, false, quantity, composition, uuid(random), QualityStatus.NOT_REVIEWED);
    }

    public static Machine activeMachine() {
        return Machine.register(UUID.randomUUID(), COMPANY, "MQ-01", "Cardadora", "Cardado", NOW);
    }

    public static Quantity grams(long grams) {
        return Quantity.positive(BigDecimal.valueOf(grams, 3));
    }
}
