package com.nodotextil.trazatex.traceability.infrastructure.persistence;

import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.Optional;
import java.util.UUID;

/** Búsquedas puntuales de un lote (las consultas de genealogía son Cypher propio). */
public interface BatchNodeRepository extends Neo4jRepository<BatchNode, UUID> {

    Optional<BatchNode> findByQrCode(String qrCode);

    Optional<BatchNode> findByTraceabilityId(String traceabilityId);
}
