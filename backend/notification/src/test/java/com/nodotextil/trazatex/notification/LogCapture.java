package com.nodotextil.trazatex.notification;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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

    public void mark(String marker) {
        LoggerFactory.getLogger("com.nodotextil.trazatex.notification.LogCapture").info(marker);
    }

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
