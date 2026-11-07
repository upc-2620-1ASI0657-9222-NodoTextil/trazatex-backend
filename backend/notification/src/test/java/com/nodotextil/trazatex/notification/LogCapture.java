package com.nodotextil.trazatex.notification;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Recoge todo lo que se registra mientras vive (mensaje ya formateado y, si lo hay, la excepción con su traza) para comprobar que un secreto
 * —el token de una invitación, la clave de SendGrid— no aparece en ningún log. Sube a TRACE los loggers del servicio para que la prueba mire también
 * lo que solo se vería al depurar.
 */
public final class LogCapture implements AutoCloseable {
    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final Logger service = (Logger) LoggerFactory.getLogger("com.nodotextil.trazatex");
    private final Level serviceBefore = service.getLevel();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    public LogCapture() {
        appender.start();
        root.addAppender(appender);
        service.setLevel(Level.TRACE);
    }

    /** Registra una marca propia para que la prueba pueda comprobar que la captura está viva aunque el servicio no escriba nada en ese camino. */
    public void mark(String marker) {
        LoggerFactory.getLogger("com.nodotextil.trazatex.notification.LogCapture").info(marker);
    }

    /** Todo lo registrado hasta ahora, una cadena por registro. */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        for (ILoggingEvent event : new ArrayList<>(appender.list)) {
            String text = event.getFormattedMessage();
            if (event.getThrowableProxy() != null) text += "\n" + ThrowableProxyUtil.asString(event.getThrowableProxy());
            lines.add(text);
        }
        return lines;
    }

    @Override public void close() {
        root.detachAppender(appender);
        appender.stop();
        service.setLevel(serviceBefore);
    }
}
