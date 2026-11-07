package com.nodotextil.trazatex.notification.application;

import com.nodotextil.trazatex.notification.application.dto.DueEmail;
import com.nodotextil.trazatex.notification.application.port.out.DeferredEmailStorePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Cola en memoria con las mismas reglas que el adaptador JDBC: solo lo {@code PENDING} cambia y todo final vacía el cuerpo. */
class InMemoryDeferredEmailStore implements DeferredEmailStorePort {
    static final class Row {
        String recipient, subject, body, status = "PENDING", lastError;
        int attempts;
        Instant next;
    }
    final Map<UUID, Row> rows = new LinkedHashMap<>();
    final Clock clock;
    int claims, expirations;

    InMemoryDeferredEmailStore(Clock clock) { this.clock = clock; }

    UUID add(String recipient, int attempts) {
        var row = new Row();
        row.recipient = recipient; row.subject = "Asunto"; row.body = "Cuerpo con token-secreto"; row.attempts = attempts; row.next = clock.instant();
        UUID id = UUID.randomUUID();
        rows.put(id, row);
        return id;
    }
    @Override public int expireDue() { expirations++; return 0; }
    @Override public List<DueEmail> claimDue(int limit, Duration lease) {
        claims++;
        List<DueEmail> due = new ArrayList<>();
        for (var entry : rows.entrySet()) {
            var r = entry.getValue();
            if (due.size() < limit && r.status.equals("PENDING") && !r.next.isAfter(clock.instant())) {
                due.add(new DueEmail(entry.getKey(), r.recipient, r.subject, r.body, r.attempts));
                r.next = clock.instant().plus(lease);
            }
        }
        return due;
    }
    private Row pending(UUID id) { var r = rows.get(id); return r.status.equals("PENDING") ? r : null; }
    private void finish(UUID id, String status, String reason) {
        var r = pending(id);
        if (r != null) { r.status = status; r.lastError = reason; r.body = ""; }
    }
    @Override public void markSent(UUID id) { finish(id, "SENT", null); }
    @Override public void markUndeliverable(UUID id, String reason) { finish(id, "UNDELIVERABLE", reason); }
    @Override public void markRetry(UUID id, Instant next, String reason) { var r = pending(id); if (r != null) { r.attempts++; r.next = next; r.lastError = reason; } }
    @Override public void markFailed(UUID id, String reason) { var r = pending(id); if (r != null) { r.attempts++; finish(id, "FAILED", reason); } }
    @Override public void release(UUID id, Instant next) { var r = pending(id); if (r != null) r.next = next; }
}
