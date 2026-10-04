package com.nodotextil.trazatex.traceability.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;

public final class ConcurrentQueries {

    private ConcurrentQueries() {
    }

    public static List<Long> run(int count, IntConsumer query) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            List<Callable<Long>> tasks = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                tasks.add(() -> {
                    long start = System.nanoTime();
                    query.accept(index);
                    return (System.nanoTime() - start) / 1_000_000;
                });
            }
            List<Long> millis = new ArrayList<>();
            for (Future<Long> future : pool.invokeAll(tasks)) {
                millis.add(future.get());
            }
            return millis;
        } finally {
            pool.shutdownNow();
        }
    }

    public static long percentile95(List<Long> millis) {
        List<Long> sorted = new ArrayList<>(millis);
        Collections.sort(sorted);
        int position = (int) Math.ceil(0.95 * sorted.size()) - 1;
        return sorted.get(position);
    }
}
