package com.nodotextil.trazatex.traceability.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Cómo pueden llegar los eventos. Kafka conserva el orden de una misma clave dentro de un tópico (un "flujo"); entre flujos
 * distintos no hay orden. Un orden es un arreglo de índices de evento (planos: los flujos uno tras otro).
 */
final class Interleavings {

    private Interleavings() {
    }

    /**
     * Un generador por corrida, reproducible. Con {@code new Random(semilla + corrida)} las semillas consecutivas dan
     * secuencias casi iguales (medido: en 400 corridas el evento 4 nunca caía antes que el 3), y el muestreo quedaba sesgado;
     * por eso la semilla se mezcla (SplitMix64) antes de usarla.
     */
    static Random seeded(long seed, long run) {
        long z = seed + run * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return new Random(z ^ (z >>> 31));
    }

    /** Todas las mezclas de los flujos que respetan el orden interno de cada uno: n! / (l1! l2! ...). */
    static List<int[]> all(int[] streamLengths) {
        int[] offsets = offsets(streamLengths);
        List<int[]> result = new ArrayList<>();
        walk(streamLengths, offsets, new int[streamLengths.length], new int[total(streamLengths)], 0, result);
        return result;
    }

    private static void walk(int[] lengths, int[] offsets, int[] taken, int[] current, int filled, List<int[]> out) {
        if (filled == current.length) {
            out.add(current.clone());
            return;
        }
        for (int stream = 0; stream < lengths.length; stream++) {
            if (taken[stream] < lengths[stream]) {
                current[filled] = offsets[stream] + taken[stream];
                taken[stream]++;
                walk(lengths, offsets, taken, current, filled + 1, out);
                taken[stream]--;
            }
        }
    }

    /** Una mezcla al azar, uniforme entre las posibles: se elige el siguiente flujo con probabilidad proporcional a lo que le queda. */
    static int[] random(int[] streamLengths, Random random) {
        int[] offsets = offsets(streamLengths);
        int[] taken = new int[streamLengths.length];
        int[] order = new int[total(streamLengths)];
        for (int filled = 0; filled < order.length; filled++) {
            int remaining = order.length - filled;
            int pick = random.nextInt(remaining);
            for (int stream = 0; stream < streamLengths.length; stream++) {
                int left = streamLengths[stream] - taken[stream];
                if (pick < left) {
                    order[filled] = offsets[stream] + taken[stream];
                    taken[stream]++;
                    break;
                }
                pick -= left;
            }
        }
        return order;
    }

    /** Una permutación cualquiera, sin respetar el orden de los flujos (peor que lo que hace Kafka). */
    static int[] freePermutation(int events, Random random) {
        int[] order = new int[events];
        for (int i = 0; i < events; i++) {
            order[i] = i;
        }
        for (int i = events - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = order[i];
            order[i] = order[j];
            order[j] = swap;
        }
        return order;
    }

    /** Reentregas (at-least-once): {@code copies} eventos se repiten en una posición posterior a su primera entrega. */
    static int[] withDuplicates(int[] order, int copies, Random random) {
        List<Integer> sequence = new ArrayList<>();
        for (int event : order) {
            sequence.add(event);
        }
        for (int copy = 0; copy < copies; copy++) {
            int original = random.nextInt(order.length);
            int event = order[original];
            int firstAt = sequence.indexOf(event);
            int position = firstAt + 1 + random.nextInt(sequence.size() - firstAt);
            sequence.add(position, event);
        }
        return sequence.stream().mapToInt(Integer::intValue).toArray();
    }

    static int total(int[] lengths) {
        int total = 0;
        for (int length : lengths) {
            total += length;
        }
        return total;
    }

    private static int[] offsets(int[] lengths) {
        int[] offsets = new int[lengths.length];
        for (int i = 1; i < lengths.length; i++) {
            offsets[i] = offsets[i - 1] + lengths[i - 1];
        }
        return offsets;
    }
}
