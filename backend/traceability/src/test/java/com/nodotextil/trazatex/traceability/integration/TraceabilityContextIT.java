package com.nodotextil.trazatex.traceability.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

/** El servicio arranca completo contra Neo4j real (la misma edición community que el docker compose). */
class TraceabilityContextIT extends IntegrationTestBase {

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void theApplicationStartsAndTalksToNeo4j() {
        assertThat(neo4j.query("RETURN 1 AS one").fetch().one()).hasValueSatisfying(row -> assertThat(row).containsEntry("one", 1L));
    }

    @Test
    void theNeo4jTransactionManagerIsTheOnlyOneSoTheIdempotentHandlerSharesItsTransactionWithTheGraph() {
        assertThat(transactionManager.getClass().getSimpleName()).isEqualTo("Neo4jTransactionManager");
    }

    @Test
    void theTestServerIsTheCommunityEditionUsedLocally() {
        assertThat(neo4j.query("CALL dbms.components() YIELD versions, edition RETURN versions[0] AS version, edition").fetch().one())
                .hasValueSatisfying(row -> {
                    assertThat(row.get("version").toString()).startsWith("5.26");
                    assertThat(row).containsEntry("edition", "community");
                });
    }
}
