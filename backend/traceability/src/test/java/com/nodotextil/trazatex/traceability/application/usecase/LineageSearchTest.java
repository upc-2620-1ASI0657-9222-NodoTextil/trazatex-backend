package com.nodotextil.trazatex.traceability.application.usecase;

import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import com.nodotextil.trazatex.traceability.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.traceability.domain.model.view.Access;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleBatchPage;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleBatchSummary;
import com.nodotextil.trazatex.traceability.domain.service.LineageAuthorizationPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.nodotextil.trazatex.traceability.application.usecase.FakeLineageReadPort.COMPANY_A;
import static com.nodotextil.trazatex.traceability.application.usecase.FakeLineageReadPort.COMPANY_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La búsqueda (US30): el QR es exacto para cualquiera; el Traceability ID es por prefijo solo sobre los lotes de la propia empresa y, en
 * los de otra, solo exacto (un prefijo como {@code TT-} no lista lotes ajenos).
 */
class LineageSearchTest {

    private final FakeLineageReadPort graph = new FakeLineageReadPort();
    private final LineageQueryService service = new LineageQueryService(graph, new LineageAuthorizationPolicy());

    private static List<String> traceabilityIds(VisibleBatchPage page) {
        return page.items().stream().map(VisibleBatchSummary::traceabilityId).toList();
    }

    // ---- QR ----

    @Test
    void aQrCodeFindsABatchOfAnyCompanyByExactMatch() {
        graph.batch("MAT", COMPANY_A).batch("AJEN", COMPANY_B).companyName(COMPANY_B, "Tintorería del Sur SAC");

        VisibleBatchPage own = service.search(COMPANY_A, "QR-MAT", null, null, null);
        VisibleBatchPage foreign = service.search(COMPANY_A, "QR-AJEN", null, null, null);

        assertThat(own.items()).singleElement().satisfies(item -> assertThat(item.access()).isEqualTo(Access.OWN));
        assertThat(foreign.items()).singleElement().satisfies(item -> {
            assertThat(item.access()).isEqualTo(Access.SHARED);
            assertThat(item.traceabilityId()).isEqualTo("TT-AJEN");
            assertThat(item.businessName()).isEqualTo("Tintorería del Sur SAC");
        });
        assertThat(service.search(COMPANY_A, "QR-AJE", null, null, null).items()).as("un QR parcial no encuentra nada").isEmpty();
    }

    @Test
    void whatIsTypedByHandIsNormalizedLowercaseAndTheConfusableLetters() {
        graph.batch("B011", COMPANY_B); // el Traceability ID TT-B011 y su QR QR-B011 (los códigos reales no tienen O, I ni L)

        assertThat(service.search(COMPANY_A, "qr-b0il", null, null, null).items()).as("minúsculas, I→1 y L→1").hasSize(1);
        assertThat(service.search(COMPANY_A, "QR-BOIL", null, null, null).items()).as("O→0, I→1 y L→1").hasSize(1);
        assertThat(service.search(COMPANY_A, null, "tt-bo1l", null, null).items()).as("Traceability ID exacto de otra empresa, con O→0 y L→1").hasSize(1);
    }

    // ---- Traceability ID ----

    @Test
    void aPrefixListsTheOwnBatchesAndNeverThoseOfAnotherCompany() {
        graph.batch("AAA1", COMPANY_A).batch("AAA2", COMPANY_A).batch("AAB1", COMPANY_A).batch("AAA9", COMPANY_B).batch("ZZZ1", COMPANY_B);

        VisibleBatchPage tt = service.search(COMPANY_A, null, "TT-", null, null);
        VisibleBatchPage aaa = service.search(COMPANY_A, null, "TT-AAA", null, null);

        assertThat(traceabilityIds(tt)).as("TT- lista solo los de mi empresa").containsExactlyInAnyOrder("TT-AAA1", "TT-AAA2", "TT-AAB1");
        assertThat(tt.items()).extracting(VisibleBatchSummary::access).containsOnly(Access.OWN);
        assertThat(traceabilityIds(aaa)).as("TT-AAA no trae el TT-AAA9 de la otra empresa").containsExactlyInAnyOrder("TT-AAA1", "TT-AAA2");
        assertThat(service.search(COMPANY_B, null, "TT-", null, null).items()).extracting(VisibleBatchSummary::traceabilityId).containsExactlyInAnyOrder("TT-AAA9", "TT-ZZZ1");
    }

    @Test
    void anotherCompanysBatchIsFoundOnlyByItsExactTraceabilityId() {
        graph.batch("AAA1", COMPANY_A).batch("AAA9", COMPANY_B);

        assertThat(traceabilityIds(service.search(COMPANY_A, null, "TT-AAA9", null, null))).containsExactly("TT-AAA9");
        assertThat(service.search(COMPANY_A, null, "TT-AAA9", null, null).items()).singleElement().satisfies(item -> assertThat(item.access()).isEqualTo(Access.SHARED));
        assertThat(service.search(COMPANY_A, null, "TT-AAA", null, null).items()).as("el prefijo del ajeno no sirve")
                .extracting(VisibleBatchSummary::traceabilityId).containsExactly("TT-AAA1");
    }

    @Test
    void anExactMatchOfTheOwnBatchAppearsOnceNotTwice() {
        graph.batch("AAA1", COMPANY_A);

        VisibleBatchPage page = service.search(COMPANY_A, null, "TT-AAA1", null, null);

        assertThat(traceabilityIds(page)).containsExactly("TT-AAA1");
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void withBothCriteriaTheBatchMustMatchBothUnderTheSameRules() {
        graph.batch("AAA1", COMPANY_A).batch("BBB1", COMPANY_B);

        assertThat(service.search(COMPANY_A, "QR-AAA1", "TT-AA", null, null).items()).as("propio: el prefijo basta").hasSize(1);
        assertThat(service.search(COMPANY_A, "QR-AAA1", "TT-BB", null, null).items()).as("propio: otro prefijo no coincide").isEmpty();
        assertThat(service.search(COMPANY_A, "QR-BBB1", "TT-BB", null, null).items()).as("ajeno: un prefijo no basta").isEmpty();
        assertThat(service.search(COMPANY_A, "QR-BBB1", "TT-BBB1", null, null).items()).as("ajeno: el exacto sí").hasSize(1);
    }

    @Test
    void incompleteBatchesNeverAppearInASearch() {
        graph.batch("AAA1", COMPANY_A).provisional("AAA2");
        graph.replace("AAA2", new com.nodotextil.trazatex.traceability.domain.model.graph.BatchData(FakeLineageReadPort.id("AAA2"), "TT-AAA2", "QR-AAA2", COMPANY_A, "Algodón",
                null, null, false, false, FakeLineageReadPort.T0, null, null, null, null, null, false)); // le falta la fase

        assertThat(traceabilityIds(service.search(COMPANY_A, null, "TT-AAA", null, null))).containsExactly("TT-AAA1");
        assertThat(service.search(COMPANY_A, "QR-AAA2", null, null, null).items()).isEmpty();
        assertThat(service.search(COMPANY_B, null, "TT-AAA2", null, null).items()).isEmpty();
    }

    // ---- criterios y paginación ----

    @Test
    void aSearchWithoutAnyCriterionIsRejected() {
        for (String blank : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> service.search(COMPANY_A, blank, blank, null, null)).isInstanceOf(ValidationException.class)
                    .satisfies(e -> assertThat(((ValidationException) e).code()).isEqualTo(ErrorCodes.MISSING_SEARCH_CRITERIA));
        }
    }

    @Test
    void thePageSizeDefaultsTo20AndIsCappedAt100() {
        for (int i = 0; i < 130; i++) {
            graph.batch(String.format("N%03d", i), COMPANY_A);
        }

        VisibleBatchPage byDefault = service.search(COMPANY_A, null, "TT-N", null, null);
        VisibleBatchPage huge = service.search(COMPANY_A, null, "TT-N", 0, 100_000);

        assertThat(byDefault.size()).isEqualTo(20);
        assertThat(byDefault.items()).hasSize(20);
        assertThat(byDefault.totalElements()).isEqualTo(130);
        assertThat(byDefault.totalPages()).isEqualTo(7);
        assertThat(huge.size()).as("si piden más, se usa 100").isEqualTo(100);
        assertThat(huge.items()).hasSize(100);
        assertThat(service.search(COMPANY_A, null, "TT-N", 1, 100).items()).as("la página 1 de 100 trae los 30 restantes").hasSize(30);
    }

    @Test
    void invalidPaginationIsRejected() {
        graph.batch("A1", COMPANY_A);

        assertThatThrownBy(() -> service.search(COMPANY_A, null, "TT-", -1, 10)).isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).code()).isEqualTo(ErrorCodes.INVALID_PAGINATION));
        assertThatThrownBy(() -> service.search(COMPANY_A, null, "TT-", 0, 0)).isInstanceOf(ValidationException.class);
    }

    @Test
    void theForeignExactMatchTakesTheFirstSlotAndThePagesOfTheOwnBatchesFollowWithoutGapsOrRepeats() {
        // "TT-A1" es el exacto de la otra empresa; mis lotes TT-A1x, TT-A2.. comparten el prefijo "TT-A1"? no: el prefijo propio es "TT-A1"
        graph.batch("A1", COMPANY_B);
        for (int i = 0; i < 5; i++) {
            graph.batch("A1" + i, COMPANY_A);
        }

        VisibleBatchPage first = service.search(COMPANY_A, null, "TT-A1", 0, 3);
        VisibleBatchPage second = service.search(COMPANY_A, null, "TT-A1", 1, 3);
        VisibleBatchPage third = service.search(COMPANY_A, null, "TT-A1", 2, 3);

        assertThat(first.totalElements()).as("5 propios + 1 exacto ajeno").isEqualTo(6);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(traceabilityIds(first).get(0)).as("el exacto ajeno va primero").isEqualTo("TT-A1");
        assertThat(first.items()).hasSize(3);
        assertThat(second.items()).hasSize(3);
        assertThat(third.items()).isEmpty();
        List<String> all = new java.util.ArrayList<>(traceabilityIds(first));
        all.addAll(traceabilityIds(second));
        assertThat(all).as("sin huecos ni repetidos").doesNotHaveDuplicates().containsExactlyInAnyOrder("TT-A1", "TT-A10", "TT-A11", "TT-A12", "TT-A13", "TT-A14");
    }
}
