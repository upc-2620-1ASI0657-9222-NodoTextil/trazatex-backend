package com.nodotextil.trazatex.quality.acceptance;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

public class World {

    public static final UUID COMPANY_A = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    public final Who adminA = new Who(UUID.fromString("00000000-0000-0000-0000-00000000a0ad"), "COMPANY_ADMIN", COMPANY_A);

    public String story;
    public Supplier<Reply> pending;
    public Reply last;
    public Instant sentAt;
    public Instant answeredAt;

    public UUID batch;
    public UUID control;
    public UUID failure;
    public java.util.Map<String, Long> tablesBefore = new java.util.TreeMap<>();
    public java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();

    public Reply submit() {
        if (pending == null) {
            throw new IllegalStateException("El escenario no preparó ninguna petición para confirmar");
        }
        return send(pending);
    }

    public Reply send(Supplier<Reply> request) {
        sentAt = Instant.now();
        last = request.get();
        answeredAt = Instant.now();
        return last;
    }
}
