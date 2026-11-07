package com.nodotextil.trazatex.quality.integration;

import com.nodotextil.trazatex.quality.application.dto.ControlReport;
import com.nodotextil.trazatex.quality.application.exception.PdfGenerationException;
import com.nodotextil.trazatex.quality.application.port.out.PdfGeneratorPort;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * US44 esc. 2 («el servicio de generación de PDF no responde») sin tocar el servicio: un interruptor alrededor del generador REAL. Encendido
 * (lo normal) delega en él; apagado, responde como un generador que no contesta. Los escenarios lo manejan con {@link #stop()} / {@link #start()}.
 * Vive aquí, y no en {@code acceptance}, porque el puerto es del servicio y los pasos solo hablan con él por HTTP.
 */
public final class PdfSwitch implements PdfGeneratorPort {

    private static final AtomicBoolean RUNNING = new AtomicBoolean(true);

    private final PdfGeneratorPort real;

    private PdfSwitch(PdfGeneratorPort real) {
        this.real = real;
    }

    public static void stop() {
        RUNNING.set(false);
    }

    public static void start() {
        RUNNING.set(true);
    }

    @Override
    public byte[] render(ControlReport report) {
        if (!RUNNING.get()) {
            throw new PdfGenerationException("El generador de PDF no responde (simulado)");
        }
        return real.render(report);
    }

    /** Envuelve el {@link PdfGeneratorPort} del contexto en el interruptor. */
    public static BeanPostProcessor installer() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof PdfGeneratorPort port && !(bean instanceof PdfSwitch) ? new PdfSwitch(port) : bean;
            }
        };
    }
}
