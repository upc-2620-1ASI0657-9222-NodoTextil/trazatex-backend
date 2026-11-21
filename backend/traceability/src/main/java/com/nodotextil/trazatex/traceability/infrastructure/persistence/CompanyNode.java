package com.nodotextil.trazatex.traceability.infrastructure.persistence;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import java.util.UUID;

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
