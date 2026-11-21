package com.nodotextil.trazatex.traceability.infrastructure.persistence;

public final class LineageQueries {

    static final String COMPLETE = "b.provisional = false AND b.traceabilityId IS NOT NULL AND b.qrCode IS NOT NULL AND b.materialType IS NOT NULL "
            + "AND b.companyId IS NOT NULL AND b.phase IS NOT NULL AND b.qualityStatus IS NOT NULL AND b.createdAt IS NOT NULL AND b.finalProduct IS NOT NULL";

    public static final String FIND_BATCH = "MATCH (b:Batch {batchId: $id}) RETURN properties(b) AS batch";

    public static final String FIND_BY_QR_CODE = "MATCH (b:Batch {qrCode: $qr}) RETURN properties(b) AS batch LIMIT 1";

    public static final String FIND_BY_TRACEABILITY_ID = "MATCH (b:Batch {traceabilityId: $traceabilityId}) RETURN properties(b) AS batch LIMIT 1";

    public static final String SEARCH_BY_PREFIX = "MATCH (b:Batch) USING INDEX SEEK b:Batch(companyId, traceabilityId) WHERE b.companyId = $company AND b.traceabilityId STARTS WITH $prefix AND " + COMPLETE
            + " RETURN properties(b) AS batch ORDER BY b.createdAt DESC, b.batchId ASC SKIP $offset LIMIT $limit";

    public static final String COUNT_BY_PREFIX = "MATCH (b:Batch) USING INDEX SEEK b:Batch(companyId, traceabilityId) WHERE b.companyId = $company AND b.traceabilityId STARTS WITH $prefix AND " + COMPLETE
            + " RETURN count(b) AS total";

    public static final String PARENTS_BY_SPLIT = "UNWIND $ids AS id MATCH (b:Batch {batchId: id}) USING INDEX SEEK b:Batch(batchId) MATCH (p:Batch)-[:SPLIT_INTO]->(b) "
            + "RETURN id AS frontier, properties(p) AS neighbor ORDER BY id, p.batchId";

    public static final String PARENTS_BY_TRANSFORMATION = "UNWIND $ids AS id MATCH (b:Batch {batchId: id}) USING INDEX SEEK b:Batch(batchId) "
            + "MATCH (i:Batch)-[:INPUT_OF]->(t:Transformation)-[:PRODUCED]->(b) "
            + "RETURN id AS frontier, properties(i) AS neighbor, properties(t) AS via ORDER BY id, t.transformationId, i.batchId";

    public static final String CHILDREN_BY_SPLIT = "UNWIND $ids AS id MATCH (b:Batch {batchId: id}) USING INDEX SEEK b:Batch(batchId) MATCH (b)-[:SPLIT_INTO]->(c:Batch) "
            + "RETURN id AS frontier, properties(c) AS neighbor ORDER BY id, c.batchId";

    public static final String CHILDREN_BY_TRANSFORMATION = "UNWIND $ids AS id MATCH (b:Batch {batchId: id}) USING INDEX SEEK b:Batch(batchId) "
            + "MATCH (b)-[:INPUT_OF]->(t:Transformation)-[:PRODUCED]->(o:Batch) "
            + "RETURN id AS frontier, properties(o) AS neighbor, properties(t) AS via ORDER BY id, t.transformationId, o.batchId";

    public static final String TRANSFERS = "UNWIND $ids AS id MATCH (b:Batch {batchId: id})-[r:TRANSFERRED_VIA]->(c:Company) USING INDEX SEEK b:Batch(batchId) "
            + "RETURN id AS batchId, r.transferId AS transferId, c.companyId AS toCompanyId, r.at AS at ORDER BY r.at, r.transferId";

    public static final String BUSINESS_NAMES = "UNWIND $ids AS id MATCH (c:Company {companyId: id}) USING INDEX SEEK c:Company(companyId) WHERE c.businessName IS NOT NULL "
            + "RETURN id AS companyId, c.businessName AS businessName";

    public static final String COMPANY_PRIVACY = "UNWIND $ids AS id MATCH (c:Company {companyId: id}) USING INDEX SEEK c:Company(companyId) WHERE c.privacy IS NOT NULL "
            + "RETURN id AS companyId, c.privacy AS privacy";

    private LineageQueries() {
    }
}
