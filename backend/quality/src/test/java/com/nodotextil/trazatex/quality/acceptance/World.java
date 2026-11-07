package com.nodotextil.trazatex.quality.acceptance;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * El estado de un escenario: quién actúa, lo que el escenario ha ido creando y la última respuesta. Vive solo durante el escenario (ámbito
 * {@code cucumber-glue}, sin proxy: ver {@link AcceptanceBeans}); lo comparten los pasos de todas las clases.
 */
public class World {

    public static final UUID COMPANY_A = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    public final Who adminA = new Who(UUID.fromString("00000000-0000-0000-0000-00000000a0ad"), "COMPANY_ADMIN", COMPANY_A);

    /** La historia que se ejecuta ({@code us25}...): algunos pasos se repiten en varias historias con otra preparación, y el texto del escenario no la distingue. */
    public String story;
    /** Lo que el usuario "confirma": los pasos {@code Dado} lo preparan y los {@code Cuando} lo ejecutan. */
    public Supplier<Reply> pending;
    public Reply last;
    /** Ventana de tiempo de la última petición, para comprobar "fecha y hora" con el reloj real. */
    public Instant sentAt;
    public Instant answeredAt;

    public UUID batch;
    public UUID control;
    public UUID failure;
    /** Las filas de cada tabla antes de la operación (US44: «no lo almacena»). */
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
