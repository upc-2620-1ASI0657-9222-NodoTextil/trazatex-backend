package com.nodotextil.trazatex.traceability.bdd;

import com.nodotextil.trazatex.traceability.application.AnalyzeFailureImpactUseCase;
import com.nodotextil.trazatex.traceability.application.GetLineageUseCase;
import com.nodotextil.trazatex.traceability.application.LineageDirection;
import com.nodotextil.trazatex.traceability.application.RecordLineageUseCase;
import com.nodotextil.trazatex.traceability.domain.AffectedLot;
import com.nodotextil.trazatex.traceability.domain.FailureImpactAnalyzer;
import com.nodotextil.trazatex.traceability.domain.LineageGraph;
import com.nodotextil.trazatex.traceability.domain.LineageType;
import com.nodotextil.trazatex.traceability.support.FakeBatchInfo;
import com.nodotextil.trazatex.traceability.support.InMemoryLineageRepository;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class LineageSteps {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 20, 14, 0);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T10:00:00Z"), ZoneOffset.UTC);

    private final Map<String, UUID> lots = new HashMap<>();
    private final InMemoryLineageRepository repository = new InMemoryLineageRepository();
    private final FakeBatchInfo batchInfo = new FakeBatchInfo();
    private final RecordLineageUseCase recordLineage = new RecordLineageUseCase(repository);
    private final List<Runnable> pendingEvents = new ArrayList<>();

    private LineageGraph consulted;
    private AnalyzeFailureImpactUseCase.Result analysis;

    @Dado("que Production publica la división del lote {string} en los lotes {string}")
    public void productionPublishesADivision(String parent, String children) {
        pendingEvents.add(() -> recordLineage.recordDivision(lot(parent), lots(children), NOW));
    }

    @Y("que Production publica la transformación de los lotes {string} en los lotes {string}")
    public void productionPublishesATransformation(String inputs, String outputs) {
        pendingEvents.add(() -> recordLineage.recordTransformation(lots(inputs), lots(outputs), NOW));
    }

    @Y("que los lotes {string} están Disponibles")
    public void lotsAreAvailable(String names) {
        setPhase(names, "AVAILABLE");
    }

    @Y("que los lotes {string} están Procesados")
    public void lotsAreProcessed(String names) {
        setPhase(names, "PROCESSED");
    }

    @Cuando("el sistema procesa los eventos de Production")
    public void systemProcessesTheEvents() {
        flushEvents();
    }

    @Cuando("se consulta la trazabilidad hacia atrás del lote {string}")
    public void backwardTraceabilityIsQueried(String name) {
        flushEvents();
        GetLineageUseCase getLineage = new GetLineageUseCase(repository, batchInfo);
        consulted = getLineage.execute(lot(name), LineageDirection.BACKWARD);
    }

    @Cuando("se confirma una falla en el lote {string}")
    public void aFailureIsConfirmed(String name) {
        flushEvents();
        AnalyzeFailureImpactUseCase useCase = new AnalyzeFailureImpactUseCase(
                repository, batchInfo, new FailureImpactAnalyzer(), event -> { }, CLOCK);
        analysis = useCase.execute(UUID.randomUUID(), lot(name));
    }

    @Entonces("existe una relación {word} de {string} hacia {string}")
    public void aRelationExists(String type, String parent, String child) {
        assertThat(repository.edges()).anySatisfy(edge -> {
            assertThat(edge.type()).isEqualTo(LineageType.valueOf(type));
            assertThat(edge.parentBatchId()).isEqualTo(lot(parent));
            assertThat(edge.childBatchId()).isEqualTo(lot(child));
        });
    }

    @Entonces("el sistema registra {int} relaciones {word}")
    public void theSystemRecordsRelations(int count, String type) {
        assertThat(repository.edges()).hasSize(count)
                .allSatisfy(edge -> assertThat(edge.type()).isEqualTo(LineageType.valueOf(type)));
    }

    @Entonces("el grafo contiene exactamente {int} relaciones")
    public void theGraphHasExactly(int count) {
        assertThat(repository.edges()).hasSize(count);
    }

    @Entonces("los antecesores del lote son {string}")
    public void theAncestorsAre(String names) {
        Set<UUID> ancestors = consulted.batchIds();
        ancestors.remove(consulted.rootBatchId());
        assertThat(namesOf(ancestors)).containsExactlyInAnyOrder(split(names));
    }

    @Entonces("los lotes con posible falla derivada son {string}")
    public void theDerivedFailureLotsAre(String names) {
        List<UUID> affected = analysis.affectedLots().stream().map(AffectedLot::batchId).toList();
        assertThat(namesOf(affected)).containsExactlyInAnyOrder(split(names));
    }

    @Y("los lotes {string} no se devuelven a operación")
    public void historicalLotsAreNotReturned(String names) {
        List<UUID> returned = analysis.affectedLots().stream().map(AffectedLot::batchId).toList();
        assertThat(returned).doesNotContainAnyElementsOf(lots(names));
    }

    private void flushEvents() {
        pendingEvents.forEach(Runnable::run);
        pendingEvents.clear();
    }

    private void setPhase(String names, String phase) {
        for (UUID id : lots(names)) {
            batchInfo.put(id, phase);
        }
    }

    private UUID lot(String name) {
        return lots.computeIfAbsent(name, key -> {
            UUID id = UUID.randomUUID();
            batchInfo.put(id, "AVAILABLE");
            return id;
        });
    }

    private List<UUID> lots(String names) {
        return Arrays.stream(split(names)).map(this::lot).toList();
    }

    private static String[] split(String names) {
        return names.split("\\s*,\\s*");
    }

    private String[] namesOf(Collection<UUID> ids) {
        return lots.entrySet().stream()
                .filter(entry -> ids.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .toArray(String[]::new);
    }
}
