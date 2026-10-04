package com.nodotextil.trazatex.traceability.infrastructure.web;

import com.nodotextil.trazatex.shared.security.JwtConfiguration;
import com.nodotextil.trazatex.shared.security.SecurityConfig;
import com.nodotextil.trazatex.traceability.application.GetSharedLineageUseCase;
import com.nodotextil.trazatex.traceability.application.LineageDirection;
import com.nodotextil.trazatex.traceability.application.LotNotFoundException;
import com.nodotextil.trazatex.traceability.application.SearchLineageUseCase;
import com.nodotextil.trazatex.traceability.application.view.LineageView;
import com.nodotextil.trazatex.traceability.application.view.LotView;
import com.nodotextil.trazatex.traceability.domain.LineageEdge;
import com.nodotextil.trazatex.traceability.domain.LineageType;
import com.nodotextil.trazatex.traceability.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TraceabilityController.class)
@Import({ SecurityConfig.class, JwtConfiguration.class })
class TraceabilityControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 20, 14, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetSharedLineageUseCase getSharedLineage;

    @MockitoBean
    private SearchLineageUseCase searchLineage;

    private final UUID userId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID batchId = UUID.randomUUID();
    private final UUID parentId = UUID.randomUUID();

    @Test
    void returnsTheAncestorsOfTheBatchForTheCompanyInTheToken() throws Exception {
        when(getSharedLineage.execute(batchId, companyId, LineageDirection.BACKWARD))
                .thenReturn(viewOf(companyId, Map.of()));

        mockMvc.perform(get("/api/traceability/batches/{id}/ancestors", batchId)
                        .with(TestJwt.companyUser(userId, companyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootBatchId").value(batchId.toString()))
                .andExpect(jsonPath("$.lots[0].traceabilityId").value("TRZ-001"))
                .andExpect(jsonPath("$.edges[0].type").value("DIVISION"));
    }

    @Test
    void aQueryBetweenCompaniesIsNotBlockedAndOnlyShowsAuthorizedData() throws Exception {
        UUID otherCompany = UUID.randomUUID();
        when(getSharedLineage.execute(batchId, companyId, LineageDirection.FULL))
                .thenReturn(viewOf(otherCompany, Map.of("supplierName", "Hilados SAC")));

        mockMvc.perform(get("/api/traceability/batches/{id}/lineage", batchId)
                        .with(TestJwt.companyUser(userId, companyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lots[0].responsibleCompanyId").value(otherCompany.toString()))
                .andExpect(jsonPath("$.lots[0].sharedData.supplierName").value("Hilados SAC"))
                .andExpect(jsonPath("$.lots[0].sharedData.quantityKg").doesNotExist());
    }

    @Test
    void aLicenseOwnerWithoutCompanyQueriesAsAnOutsider() throws Exception {
        when(getSharedLineage.execute(batchId, null, LineageDirection.FORWARD))
                .thenReturn(viewOf(companyId, Map.of()));

        mockMvc.perform(get("/api/traceability/batches/{id}/descendants", batchId)
                        .with(TestJwt.licenseOwner(userId)))
                .andExpect(status().isOk());
    }

    @Test
    void answers404WhenTheBatchDoesNotExist() throws Exception {
        when(getSharedLineage.execute(batchId, companyId, LineageDirection.BACKWARD))
                .thenThrow(new LotNotFoundException(batchId));

        mockMvc.perform(get("/api/traceability/batches/{id}/ancestors", batchId)
                        .with(TestJwt.companyUser(userId, companyId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BATCH_NOT_FOUND"));
    }

    @Test
    void searchesALineageByQrCode() throws Exception {
        when(searchLineage.byQrCode("QR-001", companyId))
                .thenReturn(List.of(lot(companyId, Map.of())));

        mockMvc.perform(get("/api/traceability/lineages").param("qrCode", "QR-001")
                        .with(TestJwt.companyUser(userId, companyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].traceabilityId").value("TRZ-001"));
    }

    @Test
    void rejectsRequestsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/traceability/batches/{id}/ancestors", batchId))
                .andExpect(status().isUnauthorized());
    }

    private LineageView viewOf(UUID owner, Map<String, Object> sharedData) {
        return new LineageView(
                batchId,
                List.of(lot(owner, sharedData)),
                List.of(new LineageEdge(parentId, batchId, LineageType.DIVISION, NOW)));
    }

    private LotView lot(UUID owner, Map<String, Object> sharedData) {
        return new LotView(batchId, "TRZ-001", owner, "YARN", "AVAILABLE", false, NOW, sharedData);
    }
}
