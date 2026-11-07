package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Factory Method de todo lote nuevo —registro, hijo de una división o salida de una transformación—: siempre le asigna
 * Traceability ID y QR nuevos y lo deja {@code AVAILABLE} y sin retención. Los códigos son aleatorios; la fábrica
 * consulta un {@code Predicate} de "código ya tomado" (que implementa la persistencia) y sortea otro hasta
 * {@value #MAX_CODE_ATTEMPTS} veces. La restricción {@code UNIQUE} de la base queda como última barrera ante una
 * carrera entre dos transacciones.
 */
public final class BatchFactory {

    /** Datos que da el operario al registrar un lote (US12). */
    public record Registration(
            String name,
            String materialType,
            Composition composition,
            Quantity quantity,
            String origin,
            String supplierName,
            String receptionCharacteristics) {
    }

    /** Una salida de transformación, tal como la declara el operario al finalizar (US18, US20). */
    public record OutputSpec(
            String name,
            String materialType,
            Quantity quantity,
            boolean finalProduct,
            CommercialData commercialData) {
    }

    /** Un hijo de la división: cantidad y, opcionalmente, nombre (por defecto el del padre). */
    public record ChildSpec(String name, Quantity quantity) {
    }

    /** El padre ya en {@code SPLIT} y sus hijos nuevos, en el orden pedido. */
    public record SplitResult(Batch parent, List<Batch> children) {
    }

    public static final int MAX_CODE_ATTEMPTS = 10;

    private final RandomGenerator random;
    private final Supplier<UUID> ids;
    private final Predicate<String> codeTaken;

    public BatchFactory(RandomGenerator random, Supplier<UUID> ids) {
        this(random, ids, code -> false);
    }

    /** {@code codeTaken} dice si un Traceability ID o un QR ya existe (en cualquiera de los dos campos). */
    public BatchFactory(RandomGenerator random, Supplier<UUID> ids, Predicate<String> codeTaken) {
        this.random = random;
        this.ids = ids;
        this.codeTaken = codeTaken;
    }

    /** Fábrica de producción: {@link SecureRandom} (los códigos no deben ser adivinables) y UUID aleatorios. */
    public static BatchFactory secure() {
        return secure(code -> false);
    }

    public static BatchFactory secure(Predicate<String> codeTaken) {
        return new BatchFactory(new SecureRandom(), UUID::randomUUID, codeTaken);
    }

    /** Registro (US12). Obligatorios: origen, tipo de material, composición, cantidad y características de recepción. */
    public Batch register(Registration registration, UUID companyId, UUID createdBy, Instant now) {
        new RequiredFields()
                .text("origin", registration.origin())
                .text("materialType", registration.materialType())
                .value("composition", registration.composition())
                .value("quantity", registration.quantity())
                .text("receptionCharacteristics", registration.receptionCharacteristics())
                .verify();
        UUID id = ids.get();
        TraceabilityId traceabilityId = newTraceabilityId();
        return new Batch(new BatchSnapshot(id, traceabilityId, newQrCode(), companyId,
                nameOrDefault(registration.name(), traceabilityId.value()),
                registration.materialType().trim(), registration.quantity(), registration.composition(),
                BatchPhase.AVAILABLE, QualityStatus.NOT_REVIEWED, false, false, BatchOriginType.REGISTERED,
                registration.origin().trim(), trimToNull(registration.supplierName()),
                registration.receptionCharacteristics().trim(), createdBy, now, CommercialData.EMPTY, 0));
    }

    /**
     * División (US15, RF-025…027): el padre debe estar habilitado, dos o más hijos y la suma de sus cantidades igual a la
     * del padre, exactamente. Si algo falla no cambia nada. El padre pasa a {@code SPLIT}; los hijos nacen con
     * Traceability ID y QR nuevos.
     */
    public SplitResult split(Batch parent, List<ChildSpec> specs, UUID createdBy, Instant now) {
        if (specs == null || specs.size() < 2) {
            throw new ValidationException(ErrorCodes.SPLIT_REQUIRES_TWO_CHILDREN,
                    "Una división necesita al menos dos lotes hijos.", null);
        }
        Quantity total = Quantity.ZERO;
        for (ChildSpec spec : specs) {
            if (spec == null || spec.quantity() == null) {
                throw new ValidationException(ErrorCodes.INVALID_QUANTITY, "Cada lote hijo necesita una cantidad.", null);
            }
            total = total.plus(spec.quantity());
        }
        if (total.compareTo(parent.quantity()) != 0) {
            throw new ValidationException(ErrorCodes.SPLIT_QUANTITIES_MISMATCH,
                    "Las cantidades no coinciden: los hijos suman " + total + " y el lote padre tiene "
                            + parent.quantity() + ".", null);
        }
        parent.ensureEnabled();
        List<Batch> children = new ArrayList<>();
        for (ChildSpec spec : specs) {
            children.add(child(parent, spec.name(), spec.quantity(), createdBy, now));
        }
        parent.markSplit();
        return new SplitResult(parent, List.copyOf(children));
    }

    /**
     * Hijo de una división (US15): misma composición, tipo de material, origen, proveedor y características de
     * recepción del padre, y el mismo estado de calidad (decisión del usuario). Misma empresa; el padre estaba
     * habilitado, así que el hijo nace sin retención.
     */
    Batch child(Batch parent, String name, Quantity quantity, UUID createdBy, Instant now) {
        return new Batch(new BatchSnapshot(ids.get(), newTraceabilityId(), newQrCode(),
                parent.companyId(), nameOrDefault(name, parent.name()), parent.materialType(), quantity,
                parent.composition(), BatchPhase.AVAILABLE, parent.qualityStatus(), false, false, BatchOriginType.SPLIT,
                parent.origin(), parent.supplierName(), parent.receptionCharacteristics(), createdBy, now,
                CommercialData.EMPTY, 0));
    }

    /**
     * Salida de una transformación (US18): {@code NOT_REVIEWED}, composición ya calculada. Origen, proveedor y
     * características de recepción son de lo que se registra, no de lo que se produce: quedan vacíos.
     */
    Batch output(OutputSpec spec, Composition composition, UUID companyId, UUID createdBy, Instant now) {
        if (spec.materialType() == null || spec.materialType().isBlank()) {
            throw new ValidationException(ErrorCodes.MISSING_FIELDS,
                    "Faltan datos obligatorios: materialType.", List.of("materialType"));
        }
        if (!spec.finalProduct() && spec.commercialData() != null && !spec.commercialData().isEmpty()) {
            throw new ValidationException(ErrorCodes.INVALID_COMMERCIAL_DATA,
                    "Los datos comerciales solo aplican a un producto final.", null);
        }
        TraceabilityId traceabilityId = newTraceabilityId();
        Batch batch = new Batch(new BatchSnapshot(ids.get(), traceabilityId, newQrCode(), companyId,
                nameOrDefault(spec.name(), traceabilityId.value()), spec.materialType().trim(), spec.quantity(),
                composition, BatchPhase.AVAILABLE, QualityStatus.NOT_REVIEWED, false, false,
                BatchOriginType.TRANSFORMATION, null, null, null, createdBy, now, CommercialData.EMPTY, 0));
        if (spec.finalProduct()) {
            batch.markAsFinalProduct(spec.commercialData());
        }
        return batch;
    }

    private TraceabilityId newTraceabilityId() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            TraceabilityId candidate = TraceabilityId.generate(random);
            if (!codeTaken.test(candidate.value())) {
                return candidate;
            }
        }
        throw new IllegalStateException("No se obtuvo un Traceability ID libre en " + MAX_CODE_ATTEMPTS + " intentos");
    }

    private QrCode newQrCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            QrCode candidate = QrCode.generate(random);
            if (!codeTaken.test(candidate.value())) {
                return candidate;
            }
        }
        throw new IllegalStateException("No se obtuvo un QR libre en " + MAX_CODE_ATTEMPTS + " intentos");
    }

    private static String nameOrDefault(String name, String fallback) {
        String value = name == null || name.isBlank() ? fallback.substring(0, Math.min(20, fallback.length())) : name.trim();
        if (value.codePointCount(0, value.length()) > 20) {
            throw new ValidationException("INVALID_BATCH_NAME", "El nombre del lote admite hasta 20 caracteres.", List.of("name"));
        }
        return value;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
