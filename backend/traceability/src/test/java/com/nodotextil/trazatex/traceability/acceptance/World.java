package com.nodotextil.trazatex.traceability.acceptance;

import com.nodotextil.trazatex.traceability.integration.EventMessages.Message;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El estado de un escenario: las empresas y lotes que creó y los eventos que "los demás servicios" van a publicar cuando ocurra lo que
 * dice el {@code Cuando} (los pasos {@code Dado} los preparan y los {@code Cuando} los publican). Vive solo durante el escenario
 * (ámbito {@code cucumber-glue}, sin proxy: ver {@link AcceptanceBeans}).
 */
public class World {

    public static final Instant T0 = Instant.parse("2026-10-21T10:00:00Z");

    public final UUID companyA = UUID.randomUUID();
    public final UUID companyB = UUID.randomUUID();

    /** Lo que ocurre cuando el escenario dice "cuando": el estado previo ya se publicó y se procesó. */
    public final List<Message> pending = new ArrayList<>();

    /** Los lotes del escenario por nombre (el nombre es también la base de su Traceability ID y su QR). */
    public final Map<String, UUID> ids = new LinkedHashMap<>();
    /** Lo que respondió la API a la consulta del {@code Cuando}. */
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
