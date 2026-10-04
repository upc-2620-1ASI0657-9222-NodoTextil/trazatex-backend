package com.nodotextil.trazatex.production.domain;

import java.util.Optional;
import java.util.UUID;

public interface MachineRepository {

    Machine save(Machine machine);

    Optional<Machine> findById(UUID id);
}
