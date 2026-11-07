package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Composición de un lote (US12): uno o más componentes cuyos porcentajes suman <b>exactamente</b> 100,00. Sin
 * componentes repetidos (sin distinguir mayúsculas). Inmutable.
 */
public final class Composition {

    public static final BigDecimal HUNDRED = new BigDecimal("100");
    /** 100,00 % expresado en centésimas. */
    static final int TOTAL_HUNDREDTHS = 10_000;

    private final List<CompositionComponent> components;

    private Composition(List<CompositionComponent> components) {
        this.components = components;
    }

    public static Composition of(List<CompositionComponent> components) {
        if (components == null || components.isEmpty()) {
            throw invalid("debe tener al menos un componente.");
        }
        Set<String> keys = new HashSet<>();
        int total = 0;
        for (CompositionComponent component : components) {
            if (component == null) {
                throw invalid("hay un componente vacío.");
            }
            if (!keys.add(component.key())) {
                throw invalid("el componente \"" + component.name() + "\" está repetido.");
            }
            total += component.hundredths();
        }
        if (total != TOTAL_HUNDREDTHS) {
            throw invalid("los porcentajes suman " + BigDecimal.valueOf(total, CompositionComponent.SCALE).toPlainString()
                    + " % y deben sumar exactamente 100 %.");
        }
        return new Composition(List.copyOf(components));
    }

    /** Un lote de entrada: su composición y los kg que aporta a la transformación. */
    public record Contribution(Composition composition, Quantity quantity) {
    }

    /**
     * Composición resultante de varias entradas: promedio ponderado por kg (US17). Con una sola entrada devuelve la
     * misma composición. Es aritmética exacta de enteros (gramos × centésimas): cada componente vale
     * {@code Σ gramos·centésimas / Σ gramos}; las partes enteras casi siempre suman menos de 100,00 y las centésimas
     * que faltan se reparten por <b>mayor resto</b> (empate: el componente que apareció primero), así el resultado suma
     * exactamente 100,00. Un componente que quedaría en 0,00 se omite.
     */
    public static Composition weightedAverage(List<Contribution> contributions) {
        if (contributions == null || contributions.isEmpty()) {
            throw new ValidationException(ErrorCodes.NO_BATCHES_SELECTED,
                    "Se necesita al menos un lote de entrada para determinar la composición.", null);
        }
        BigInteger totalGrams = BigInteger.ZERO;
        Map<String, Accumulator> accumulators = new LinkedHashMap<>();
        for (Contribution contribution : contributions) {
            BigInteger grams = contribution.quantity().grams();
            totalGrams = totalGrams.add(grams);
            for (CompositionComponent component : contribution.composition().components()) {
                accumulators.computeIfAbsent(component.key(), key -> new Accumulator(component.name()))
                        .add(grams.multiply(BigInteger.valueOf(component.hundredths())));
            }
        }
        List<Accumulator> ordered = new ArrayList<>(accumulators.values());
        int assigned = 0;
        for (Accumulator accumulator : ordered) {
            BigInteger[] division = accumulator.numerator.divideAndRemainder(totalGrams);
            accumulator.hundredths = division[0].intValueExact();
            accumulator.remainder = division[1];
            assigned += accumulator.hundredths;
        }
        int missing = TOTAL_HUNDREDTHS - assigned;
        // sorted() es estable: ante igual resto conserva el orden de aparición
        List<Accumulator> byRemainder = ordered.stream()
                .sorted(Comparator.comparing((Accumulator accumulator) -> accumulator.remainder).reversed())
                .toList();
        for (int i = 0; i < missing; i++) {
            byRemainder.get(i).hundredths++;
        }
        List<CompositionComponent> result = new ArrayList<>();
        for (Accumulator accumulator : ordered) {
            if (accumulator.hundredths > 0) {
                result.add(new CompositionComponent(accumulator.name,
                        BigDecimal.valueOf(accumulator.hundredths, CompositionComponent.SCALE)));
            }
        }
        return of(result);
    }

    private static final class Accumulator {
        private final String name;
        private BigInteger numerator = BigInteger.ZERO;
        private BigInteger remainder = BigInteger.ZERO;
        private int hundredths;

        private Accumulator(String name) {
            this.name = name;
        }

        private void add(BigInteger value) {
            numerator = numerator.add(value);
        }
    }

    public List<CompositionComponent> components() {
        return components;
    }

    static String keyOf(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Composition composition && components.equals(composition.components);
    }

    @Override
    public int hashCode() {
        return components.hashCode();
    }

    @Override
    public String toString() {
        return components.toString();
    }

    private static ValidationException invalid(String message) {
        return new ValidationException(ErrorCodes.INVALID_COMPOSITION, "La composición no es válida: " + message, null);
    }
}
