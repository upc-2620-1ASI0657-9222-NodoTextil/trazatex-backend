package com.nodotextil.trazatex.traceability.domain.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Los nombres son los del glosario y de {@code organization-access.md}: si alguien los cambia, esta prueba lo nota. */
class DomainEnumsTest {

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    @Test
    void phasesQualityStatusesAndVisibilityUseTheGlossaryNames() {
        assertThat(names(BatchPhase.values())).containsExactly("AVAILABLE", "IN_TRANSFORMATION", "IN_TRANSFER", "IN_QUALITY_CONTROL",
                "PROCESSED", "SPLIT", "DISCARDED");
        assertThat(names(QualityStatus.values())).containsExactly("NOT_REVIEWED", "CONFORMING", "FAILED", "PENDING_REEVALUATION",
                "POTENTIAL_DERIVED_FAILURE");
        assertThat(names(Visibility.values())).containsExactly("SHARED", "PRIVATE");
        assertThat(names(TransformationType.values())).containsExactly("SPINNING", "WEAVING", "DYEING");
    }

    @Test
    void onlyProcessedSplitAndDiscardedAreHistorical() {
        assertThat(Arrays.stream(BatchPhase.values()).filter(BatchPhase::isHistorical).toList())
                .containsExactly(BatchPhase.PROCESSED, BatchPhase.SPLIT, BatchPhase.DISCARDED);
    }

    @Test
    void theElevenConfigurableFieldsAreTheOnesOrganizationAccessPublishes() {
        assertThat(names(ConfigurableField.values())).containsExactly("SUPPLIER", "ORIGIN", "QUANTITY", "COMPOSITION",
                "RECEPTION_CHARACTERISTICS", "MACHINERY", "SHRINKAGE", "WASTE", "WASTE_REASON", "FAILURE_CAUSE", "QUALITY_TEST_VALUES");
    }

    @Test
    void theFiveRelationshipsOfFigure40AndOnlyThreeAreGenealogy() {
        assertThat(names(EdgeType.values())).containsExactly("OWNS_BATCH", "INPUT_OF", "PRODUCED", "SPLIT_INTO", "TRANSFERRED_VIA");
        assertThat(Arrays.stream(EdgeType.values()).filter(EdgeType::isGenealogical).toList())
                .as("una transferencia no crea relaciones genealógicas (US29 esc. 3)")
                .containsExactly(EdgeType.INPUT_OF, EdgeType.PRODUCED, EdgeType.SPLIT_INTO);
    }
}
