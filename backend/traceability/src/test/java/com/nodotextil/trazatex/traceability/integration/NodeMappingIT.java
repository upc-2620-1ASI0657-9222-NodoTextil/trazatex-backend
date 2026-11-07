package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;
import com.nodotextil.trazatex.traceability.domain.model.TransformationType;
import com.nodotextil.trazatex.traceability.infrastructure.persistence.BatchNode;
import com.nodotextil.trazatex.traceability.infrastructure.persistence.BatchNodeRepository;
import com.nodotextil.trazatex.traceability.infrastructure.persistence.CompanyNode;
import com.nodotextil.trazatex.traceability.infrastructure.persistence.TransformationNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Los nodos de Spring Data Neo4j leen lo que los consumidores escriben con Cypher (UUID, enums, fechas, decimales y nulos). */
class NodeMappingIT extends IntegrationTestBase {

    private static final UUID BATCH = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID COMPANY = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Autowired
    BatchNodeRepository batches;
    @Autowired
    Neo4jTemplate template;

    @Test
    void aFullBatchIsReadWithEveryFieldOfTheModel() {
        neo4j.query("""
                CREATE (:Batch {batchId: $id, traceabilityId: 'TT-7K2M9Q4X1A', qrCode: 'QR123', companyId: $company,
                  materialType: 'Algodón', phase: 'IN_TRANSFORMATION', qualityStatus: 'CONFORMING', qualityHold: false,
                  finalProduct: false, createdAt: datetime('2026-10-20T12:00:00Z'), supplierName: 'Textiles del Norte',
                  origin: 'Piura', quantityKg: '12.500', composition: '[{"name":"Algodón","percentage":100.00}]',
                  receptionCharacteristics: 'Seco', provisional: false, batchVersion: 3})""")
                .bind(BATCH.toString()).to("id").bind(COMPANY.toString()).to("company").run();

        BatchNode batch = batches.findById(BATCH).orElseThrow();

        assertThat(batch.batchId()).isEqualTo(BATCH);
        assertThat(batch.traceabilityId()).isEqualTo("TT-7K2M9Q4X1A");
        assertThat(batch.qrCode()).isEqualTo("QR123");
        assertThat(batch.companyId()).isEqualTo(COMPANY);
        assertThat(batch.materialType()).isEqualTo("Algodón");
        assertThat(batch.phase()).isEqualTo(BatchPhase.IN_TRANSFORMATION);
        assertThat(batch.qualityStatus()).isEqualTo(QualityStatus.CONFORMING);
        assertThat(batch.qualityHold()).isFalse();
        assertThat(batch.finalProduct()).isFalse();
        assertThat(batch.createdAt()).isEqualTo(Instant.parse("2026-10-20T12:00:00Z"));
        assertThat(batch.supplierName()).isEqualTo("Textiles del Norte");
        assertThat(batch.origin()).isEqualTo("Piura");
        assertThat(batch.quantityKg()).isEqualByComparingTo(new BigDecimal("12.500"));
        assertThat(batch.composition()).contains("Algodón");
        assertThat(batch.receptionCharacteristics()).isEqualTo("Seco");
        assertThat(batch.provisional()).isFalse();
        assertThat(batch.batchVersion()).isEqualTo(3L);
    }

    @Test
    void aProvisionalBatchHasOnlyItsIdAndNoVersionYet() {
        neo4j.query("CREATE (:Batch {batchId: $id, provisional: true})").bind(BATCH.toString()).to("id").run();

        BatchNode batch = batches.findById(BATCH).orElseThrow();

        assertThat(batch.provisional()).isTrue();
        assertThat(batch.batchVersion()).as("null = ningún BatchPhaseChanged aplicado todavía").isNull();
        assertThat(batch.traceabilityId()).isNull();
        assertThat(batch.phase()).isNull();
        assertThat(batch.companyId()).isNull();
    }

    @Test
    void aBatchIsFoundByQrCodeAndByTraceabilityId() {
        neo4j.query("CREATE (:Batch {batchId: $id, traceabilityId: 'TT-AAAAAAAAAA', qrCode: 'QR-1', provisional: false})")
                .bind(BATCH.toString()).to("id").run();

        assertThat(batches.findByQrCode("QR-1")).hasValueSatisfying(batch -> assertThat(batch.batchId()).isEqualTo(BATCH));
        assertThat(batches.findByTraceabilityId("TT-AAAAAAAAAA")).hasValueSatisfying(batch -> assertThat(batch.batchId()).isEqualTo(BATCH));
        assertThat(batches.findByQrCode("QR-2")).isEmpty();
    }

    @Test
    void aTransformationIsReadWithItsConfigurableFieldsAndItsCompany() {
        UUID transformation = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
        neo4j.query("""
                CREATE (:Transformation {transformationId: $id, companyId: $company, type: 'DYEING',
                  startedAt: datetime('2026-10-20T10:00:00Z'), completedAt: datetime('2026-10-20T11:30:00Z'),
                  machineName: 'Cardadora', machineType: 'Cardado', shrinkageKg: '3.000', wasteKg: '2.000', wasteReason: 'Orillo', provisional: false})""")
                .bind(transformation.toString()).to("id").bind(COMPANY.toString()).to("company").run();

        TransformationNode node = template.findById(transformation, TransformationNode.class).orElseThrow();

        assertThat(node.companyId()).isEqualTo(COMPANY);
        assertThat(node.type()).isEqualTo(TransformationType.DYEING);
        assertThat(node.startedAt()).isEqualTo(Instant.parse("2026-10-20T10:00:00Z"));
        assertThat(node.completedAt()).isEqualTo(Instant.parse("2026-10-20T11:30:00Z"));
        assertThat(node.machineName()).isEqualTo("Cardadora");
        assertThat(node.machineType()).isEqualTo("Cardado");
        assertThat(node.shrinkageKg()).isEqualByComparingTo("3");
        assertThat(node.wasteKg()).isEqualByComparingTo("2");
        assertThat(node.wasteReason()).isEqualTo("Orillo");
    }

    @Test
    void aCompanyWithoutPrivacyHasNoMapYet() {
        neo4j.query("CREATE (:Company {companyId: $id})").bind(COMPANY.toString()).to("id").run();
        UUID configured = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
        neo4j.query("CREATE (:Company {companyId: $id, privacy: '{\"SUPPLIER\":\"SHARED\"}'})").bind(configured.toString()).to("id").run();

        assertThat(template.findById(COMPANY, CompanyNode.class)).hasValueSatisfying(company -> assertThat(company.privacy()).isNull());
        assertThat(template.findById(configured, CompanyNode.class)).hasValueSatisfying(company -> assertThat(company.privacy()).contains("SUPPLIER"));
    }
}
