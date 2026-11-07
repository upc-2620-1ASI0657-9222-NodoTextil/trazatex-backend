package com.nodotextil.trazatex.quality.domain.model;

import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code impactType} se deduce de {@code commonAncestorBatchId}, que Traceability define como el punto de partida (el lote fallado u
 * uno de sus antecesores) más cercano al lote fallado desde el que se llega al afectado.
 */
class ImpactTypeTest {

    private final UUID failed = UUID.randomUUID();
    private final UUID affected = UUID.randomUUID();

    @Test
    void whenTheAffectedBatchIsTheCommonAncestorItIsAnAncestor() {
        assertThat(ImpactType.derive(failed, affected, affected)).isEqualTo(ImpactType.ANCESTOR);
    }

    @Test
    void whenTheFailedBatchIsTheCommonAncestorTheAffectedOneIsItsDescendant() {
        assertThat(ImpactType.derive(failed, affected, failed)).isEqualTo(ImpactType.DESCENDANT);
    }

    @Test
    void anyOtherCommonAncestorIsARelatedBranch() {
        assertThat(ImpactType.derive(failed, affected, UUID.randomUUID())).isEqualTo(ImpactType.RELATED_BRANCH);
    }

    @Test
    void theThreeTypesAreExclusiveAndExhaustiveForDistinctBatches() {
        UUID other = UUID.randomUUID();
        for (UUID ancestor : new UUID[]{failed, affected, other}) {
            int matches = 0;
            ImpactType type = ImpactType.derive(failed, affected, ancestor);
            matches += type == ImpactType.ANCESTOR ? 1 : 0;
            matches += type == ImpactType.DESCENDANT ? 1 : 0;
            matches += type == ImpactType.RELATED_BRANCH ? 1 : 0;
            assertThat(matches).isEqualTo(1);
        }
    }

    @Test
    void withoutTheCommonAncestorTheTypeCannotBeDeduced() {
        assertThatThrownBy(() -> ImpactType.derive(failed, affected, null)).isInstanceOfSatisfying(TrazatexException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCodes.INVALID_IMPACT));
        assertThatThrownBy(() -> ImpactType.derive(null, affected, failed)).isInstanceOf(TrazatexException.class);
        assertThatThrownBy(() -> ImpactType.derive(failed, null, failed)).isInstanceOf(TrazatexException.class);
    }
}
