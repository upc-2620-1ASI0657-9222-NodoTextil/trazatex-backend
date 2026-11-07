package com.nodotextil.trazatex.traceability.domain.service;

import com.nodotextil.trazatex.traceability.domain.model.CompanyPrivacy;
import com.nodotextil.trazatex.traceability.domain.model.graph.BatchData;
import com.nodotextil.trazatex.traceability.domain.model.graph.Lineage;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleBatchPage;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleBatchSummary;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleLineage;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * La política con la privacidad que se quiera, para probarla sola: arma el {@link Lineage} con la privacidad de las empresas ajenas como lo hace el
 * servicio (la política no guarda estado: la privacidad viaja en la consulta).
 */
final class PolicyUnderTest {

    /** La privacidad de cada empresa, como la configuraría su administrador. */
    @FunctionalInterface
    interface Lookup {

        CompanyPrivacy privacyOf(UUID companyId);

        static Lookup allPrivate() {
            return companyId -> CompanyPrivacy.ALL_PRIVATE;
        }
    }

    private final LineageAuthorizationPolicy policy = new LineageAuthorizationPolicy();
    private final Lookup lookup;

    PolicyUnderTest(Lookup lookup) {
        this.lookup = lookup;
    }

    VisibleLineage view(Lineage lineage, UUID requesterCompanyId) {
        Set<UUID> foreign = new HashSet<>();
        lineage.batches().forEach(b -> foreign.add(b.data().companyId()));
        lineage.transformations().forEach(t -> foreign.add(t.data().companyId()));
        foreign.remove(requesterCompanyId);
        Map<UUID, CompanyPrivacy> privacy = new LinkedHashMap<>();
        foreign.forEach(company -> privacy.put(company, lookup.privacyOf(company)));
        return policy.view(new Lineage(lineage.startBatchId(), lineage.direction(), lineage.batches(), lineage.transformations(), lineage.edges(), lineage.transfers(),
                lineage.companyNames(), privacy, lineage.complete(), lineage.truncated()), requesterCompanyId);
    }

    VisibleBatchSummary summary(BatchData batch, String businessName, UUID requesterCompanyId) {
        return policy.summary(batch, businessName, requesterCompanyId);
    }

    VisibleBatchPage page(List<BatchData> batches, Map<UUID, String> companyNames, UUID requesterCompanyId, int page, int size, long totalElements) {
        return policy.page(batches, companyNames, requesterCompanyId, page, size, totalElements);
    }
}
