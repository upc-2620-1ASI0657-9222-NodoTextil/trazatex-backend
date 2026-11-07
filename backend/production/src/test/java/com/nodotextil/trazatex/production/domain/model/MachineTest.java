package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.stream.Stream;

import static com.nodotextil.trazatex.production.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.NOW;
import static com.nodotextil.trazatex.production.domain.Fixtures.OPERATOR;
import static com.nodotextil.trazatex.production.domain.Fixtures.OTHER_COMPANY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US10. */
class MachineTest {

    private static Machine machine() {
        return Machine.register(UUID.randomUUID(), COMPANY, " MQ-07 ", " Telar Picanol ", "Tejeduría", NOW);
    }

    @Test
    void registersAnActiveMachine() {
        Machine machine = machine();

        assertThat(machine.status()).isEqualTo(MachineStatus.ACTIVE);
        assertThat(machine.internalCode()).isEqualTo("MQ-07");
        assertThat(machine.name()).isEqualTo("Telar Picanol");
        assertThat(machine.type()).isEqualTo("Tejeduría");
        assertThat(machine.companyId()).isEqualTo(COMPANY);
        assertThat(machine.createdAt()).isEqualTo(NOW);
    }

    @Test
    void codeNameAndTypeAreRequired() {
        assertThatThrownBy(() -> Machine.register(UUID.randomUUID(), COMPANY, " ", null, "", NOW))
                .isInstanceOfSatisfying(TrazatexException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCodes.MISSING_FIELDS);
                    assertThat(e.details()).containsExactlyInAnyOrder("internalCode", "name", "type");
                });
    }

    static Stream<Arguments> allowedChanges() {
        return Stream.of(MachineStatus.values()).flatMap(from -> Stream.of(MachineStatus.values())
                .filter(to -> to != from).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("allowedChanges")
    void anyStatusCanChangeToAnyOtherAndTheChangeIsRecorded(MachineStatus from, MachineStatus to) {
        Machine machine = Machine.restore(UUID.randomUUID(), COMPANY, "MQ-1", "M", "T", from, NOW);

        MachineStatusChange change = machine.changeStatus(to, OPERATOR, NOW.plusSeconds(30));

        assertThat(machine.status()).isEqualTo(to);
        assertThat(change.machineId()).isEqualTo(machine.id());
        assertThat(change.from()).isEqualTo(from);
        assertThat(change.to()).isEqualTo(to);
        assertThat(change.changedBy()).isEqualTo(OPERATOR);
        assertThat(change.changedAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void repeatingTheCurrentStatusIsAConflictAndChangesNothing() {
        Machine machine = machine();

        assertThatThrownBy(() -> machine.changeStatus(MachineStatus.ACTIVE, OPERATOR, NOW))
                .isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.INVALID_STATUS_TRANSITION));
        assertThat(machine.status()).isEqualTo(MachineStatus.ACTIVE);
    }

    @Test
    void onlyAnActiveMachineOfTheSameCompanyCanBeUsed() {
        Machine machine = machine();
        assertThatCode(() -> machine.ensureUsableBy(COMPANY)).doesNotThrowAnyException();
        assertThatThrownBy(() -> machine.ensureUsableBy(OTHER_COMPANY))
                .isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.MACHINE_COMPANY_MISMATCH));

        machine.changeStatus(MachineStatus.OUT_OF_SERVICE, OPERATOR, NOW);
        assertThatThrownBy(() -> machine.ensureUsableBy(COMPANY))
                .isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.MACHINE_NOT_ACTIVE));
        machine.changeStatus(MachineStatus.INACTIVE, OPERATOR, NOW);
        assertThatThrownBy(() -> machine.ensureUsableBy(COMPANY))
                .isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.MACHINE_NOT_ACTIVE));
        machine.changeStatus(MachineStatus.ACTIVE, OPERATOR, NOW);
        assertThatCode(() -> machine.ensureUsableBy(COMPANY)).doesNotThrowAnyException();
    }
}
