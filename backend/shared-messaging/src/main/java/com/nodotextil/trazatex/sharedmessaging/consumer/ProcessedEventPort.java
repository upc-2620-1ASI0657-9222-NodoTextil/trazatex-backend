package com.nodotextil.trazatex.sharedmessaging.consumer;

import java.util.UUID;

public interface ProcessedEventPort {

    boolean markProcessed(UUID eventId, String eventType);
}
