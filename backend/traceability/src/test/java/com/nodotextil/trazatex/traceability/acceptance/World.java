package com.nodotextil.trazatex.traceability.acceptance;

import com.nodotextil.trazatex.traceability.integration.EventMessages.Message;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class World {

    public static final Instant T0 = Instant.parse("2026-10-21T10:00:00Z");

    public final UUID companyA = UUID.randomUUID();
    public final UUID companyB = UUID.randomUUID();

    public final List<Message> pending = new ArrayList<>();

    public final Map<String, UUID> ids = new LinkedHashMap<>();
    public Api.Reply reply;

    public UUID batch;
    public String batchTraceabilityId;
    public String batchQrCode;
    public UUID transfer;
    public UUID transformation;
    public final List<UUID> children = new ArrayList<>();
    public final List<UUID> inputs = new ArrayList<>();
    public final List<UUID> outputs = new ArrayList<>();
}
