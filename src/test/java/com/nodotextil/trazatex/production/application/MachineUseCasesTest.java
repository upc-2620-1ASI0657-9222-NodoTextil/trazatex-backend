package com.nodotextil.trazatex.production.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodotextil.trazatex.production.domain.Machine;
import com.nodotextil.trazatex.production.domain.MachineRepository;
import com.nodotextil.trazatex.production.domain.MachineStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MachineUseCasesTest {

    private InMemoryMachineRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryMachineRepository();
    }

    @Test
    void registersMachineAsActive() {
        UUID companyId = UUID.fromString("df274222-f7be-419c-b19f-aa3d0fd065cb");
        RegisterMachineUseCase useCase = new RegisterMachineUseCase(repository);

        Machine machine = useCase.execute(new RegisterMachineUseCase.Command(
                "MC-001", "Circular knitting machine", "KNITTING", companyId));

        assertThat(machine.id()).isNotNull();
        assertThat(machine.companyId()).isEqualTo(companyId);
        assertThat(machine.status()).isEqualTo(MachineStatus.ACTIVE);
        assertThat(repository.findById(machine.id())).contains(machine);
    }

    @Test
    void changesMachineStatus() {
        Machine machine = Machine.register(
                UUID.randomUUID(),
                "MC-002",
                "Cutting machine",
                "CUTTING",
                UUID.fromString("25fc08ca-8b9e-4ae3-aa9b-a31f99d973ae"));
        repository.save(machine);
        ChangeMachineStatusUseCase useCase = new ChangeMachineStatusUseCase(repository);

        Machine updated = useCase.execute(machine.id(), MachineStatus.OUT_OF_SERVICE);

        assertThat(updated.status()).isEqualTo(MachineStatus.OUT_OF_SERVICE);
        assertThat(repository.findById(machine.id()).orElseThrow().status())
                .isEqualTo(MachineStatus.OUT_OF_SERVICE);
    }

    @Test
    void rejectsStatusChangeForUnknownMachine() {
        UUID unknownId = UUID.fromString("e48088d2-8e9d-4db9-b8c8-b22fe98bf96f");
        ChangeMachineStatusUseCase useCase = new ChangeMachineStatusUseCase(repository);

        assertThatThrownBy(() -> useCase.execute(unknownId, MachineStatus.INACTIVE))
                .isInstanceOf(MachineNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    private static final class InMemoryMachineRepository implements MachineRepository {

        private final Map<UUID, Machine> machines = new HashMap<>();

        @Override
        public Machine save(Machine machine) {
            machines.put(machine.id(), machine);
            return machine;
        }

        @Override
        public Optional<Machine> findById(UUID id) {
            return Optional.ofNullable(machines.get(id));
        }
    }
}
