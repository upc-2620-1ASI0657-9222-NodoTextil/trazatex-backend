package com.nodotextil.trazatex.traceability.infrastructure.messaging;

import com.nodotextil.trazatex.traceability.domain.exception.ImpactAnalysisIncompleteException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;

/**
 * Agotados los reintentos de un {@code FailureConfirmed} cuya familia sigue incompleta, el mensaje queda en {@code quality.failure.DLT} y <b>nadie más se entera</b>:
 * hay lotes posiblemente afectados que nadie va a retener. Este aviso (nivel {@code ERROR}) lo dice con todo lo necesario para actuar: la falla, los lotes que faltan y
 * cómo reenviar el mensaje desde la DLT (el procedimiento de {@code docs/progress.md}, que es exactamente el que prueban las pruebas).
 */
public final class ImpactDeadLetterReporter implements BiConsumer<ConsumerRecord<?, ?>, Exception> {

    /** Los dos comandos del procedimiento de reenvío, tal como están en {@code docs/progress.md} ({@code <failureId>} se reemplaza por el de la falla). */
    static final String COPY_COMMAND = "docker compose cp infra/kafka/redrive-dlt.sh kafka:/tmp/redrive-dlt.sh";
    static final String RUN_COMMAND = "docker compose exec -T kafka sh /tmp/redrive-dlt.sh localhost:9092 quality.failure.DLT quality.failure <failureId>";

    private static final Logger log = LoggerFactory.getLogger(ImpactDeadLetterReporter.class);

    private final int attempts;

    public ImpactDeadLetterReporter(int attempts) {
        this.attempts = attempts;
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        ImpactAnalysisIncompleteException incomplete = causeOf(exception);
        if (incomplete == null) {
            return;
        }
        log.error(message(String.valueOf(record.key()), incomplete, attempts));
    }

    static String message(String failureId, ImpactAnalysisIncompleteException incomplete, int attempts) {
        return "HAY LOTES POSIBLEMENTE AFECTADOS SIN RETENER: la falla " + failureId + " (lote fallado " + incomplete.sourceBatchId() + ") no se pudo analizar tras "
                + attempts + " intentos porque su familia sigue incompleta; no se publicó PossibleDerivedFailureDetected. Faltan (lotes o transformaciones provisionales o "
                + "incompletos): " + incomplete.missingNodes() + ". El mensaje quedó en quality.failure.DLT. Cuando lleguen los eventos de Production que los completan, "
                + "reenvíelo así (docs/progress.md, «Reenvío desde la DLT de quality.failure»): " + COPY_COMMAND + " && " + RUN_COMMAND.replace("<failureId>", failureId);
    }

    public static ImpactAnalysisIncompleteException causeOf(Throwable exception) {
        for (Throwable t = exception; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof ImpactAnalysisIncompleteException incomplete) {
                return incomplete;
            }
        }
        return null;
    }
}
