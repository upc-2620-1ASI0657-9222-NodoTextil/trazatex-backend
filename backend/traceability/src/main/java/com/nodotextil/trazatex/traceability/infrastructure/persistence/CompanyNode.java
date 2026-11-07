package com.nodotextil.trazatex.traceability.infrastructure.persistence;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import java.util.UUID;

/**
 * {@code (:Company)}. {@code privacy} es el JSON {@code {campo: SHARED|PRIVATE}} del último {@code CompanyPrivacyUpdated}
 * (una propiedad de Neo4j no puede ser un mapa). Sin él, o con un campo ausente, el campo es {@code PRIVATE}.
 */
@Node("Company")
public class CompanyNode {

    @Id
    @Property("companyId")
    private UUID companyId;
    private String privacy;

    protected CompanyNode() {
    }

    public CompanyNode(UUID companyId) {
        this.companyId = companyId;
    }

    public UUID companyId() {
        return companyId;
    }

    public String privacy() {
        return privacy;
    }
}
