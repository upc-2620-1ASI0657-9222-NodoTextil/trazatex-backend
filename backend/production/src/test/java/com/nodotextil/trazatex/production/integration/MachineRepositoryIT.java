package com.nodotextil.trazatex.production.integration;

import com.nodotextil.trazatex.production.domain.model.Machine;
import com.nodotextil.trazatex.production.domain.model.MachineStatus;
import com.nodotextil.trazatex.production.domain.model.MachineStatusChange;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static com.nodotextil.trazatex.production.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.OPERATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineRepositoryIT extends IntegrationTestBase {

    @Test
    void aMachineRoundTrips() {
        Machine saved = savedMachine(COMPANY, " MQ-07 ");

        Machine loaded = inTx(() -> machines.findById(saved.id()).orElseThrow());

        assertThat(loaded.companyId()).isEqualTo(COMPANY);
        assertThat(loaded.internalCode()).isEqualTo("MQ-07");
        assertThat(loaded.name()).isEqualTo("Cardadora  MQ-07");   // "Cardadora " + " MQ-07 ", recortado
        assertThat(loaded.type()).isEqualTo("Cardado");
        assertThat(loaded.status()).isEqualTo(MachineStatus.ACTIVE);
        assertThat(loaded.createdAt()).isEqualTo(START);
    }

    @Test
    void theStatusChangeIsPersistedAndTheHistoryKeepsEveryChangeInOrder() {
        Machine saved = savedMachine(COMPANY, "MQ-01");
        inTx(() -> machines.recordStatusChange(new MachineStatusChange(saved.id(), null, MachineStatus.ACTIVE, OPERATOR, START)));

        inTx(() -> {
            Machine machine = machines.findByIdForUpdate(saved.id()).orElseThrow();
            machines.recordStatusChange(machine.changeStatus(MachineStatus.OUT_OF_SERVICE, OPERATOR, START.plusSeconds(10)));
            machines.save(machine);
        });
        inTx(() -> {
            Machine machine = machines.findByIdForUpdate(saved.id()).orElseThrow();
            machines.recordStatusChange(machine.changeStatus(MachineStatus.INACTIVE, OPERATOR, START.plusSeconds(20)));
            machines.save(machine);
        });
        inTx(() -> {
            Machine machine = machines.findByIdForUpdate(saved.id()).orElseThrow();
            machines.recordStatusChange(machine.changeStatus(MachineStatus.ACTIVE, OPERATOR, START.plusSeconds(30)));
            machines.save(machine);
        });

        assertThat(inTx(() -> machines.findById(saved.id()).orElseThrow()).status()).isEqualTo(MachineStatus.ACTIVE);
        var history = inTx(() -> machines.statusHistory(saved.id()));
        assertThat(history).extracting(MachineStatusChange::from).containsExactly(null, MachineStatus.ACTIVE, MachineStatus.OUT_OF_SERVICE, MachineStatus.INACTIVE);
        assertThat(history).extracting(MachineStatusChange::to)
                .containsExactly(MachineStatus.ACTIVE, MachineStatus.OUT_OF_SERVICE, MachineStatus.INACTIVE, MachineStatus.ACTIVE);
        assertThat(history).extracting(MachineStatusChange::changedAt)
                .containsExactly(START, START.plusSeconds(10), START.plusSeconds(20), START.plusSeconds(30));
        assertThat(history).allSatisfy(change -> {
            assertThat(change.machineId()).isEqualTo(saved.id());
            assertThat(change.changedBy()).isEqualTo(OPERATOR);
        });
    }

    @Test
    void theHistoryOfOneMachineDoesNotMixWithAnother() {
        Machine one = savedMachine(COMPANY, "MQ-01");
        Machine two = savedMachine(COMPANY, "MQ-02");
        inTx(() -> machines.recordStatusChange(new MachineStatusChange(one.id(), null, MachineStatus.ACTIVE, OPERATOR, START)));

        assertThat(inTx(() -> machines.statusHistory(one.id()))).hasSize(1);
        assertThat(inTx(() -> machines.statusHistory(two.id()))).isEmpty();
    }

    @Test
    void theCodeExistsPerCompanyIgnoringCaseAndSpaces() {
        savedMachine(COMPANY, "MQ-01");

        assertThat(machines.existsByCompanyAndInternalCode(COMPANY, "MQ-01")).isTrue();
        assertThat(machines.existsByCompanyAndInternalCode(COMPANY, "mq-01")).isTrue();
        assertThat(machines.existsByCompanyAndInternalCode(COMPANY, "  Mq-01  ")).isTrue();
        assertThat(machines.existsByCompanyAndInternalCode(COMPANY, "MQ-02")).isFalse();
        assertThat(machines.existsByCompanyAndInternalCode(OTHER_COMPANY, "MQ-01")).isFalse();
    }

    @Test
    void aDuplicateCodeIsRejectedByTheDatabaseButNotAcrossCompanies() {
        savedMachine(COMPANY, "MQ-01");

        assertThatThrownBy(() -> savedMachine(COMPANY, "mq-01")).isInstanceOf(DataIntegrityViolationException.class);
        savedMachine(OTHER_COMPANY, "MQ-01");
    }

    @Test
    void listsTheMachinesOfACompanyByCodeWithAnOptionalStatusFilter() {
        Machine b = savedMachine(COMPANY, "B-2");
        Machine a = savedMachine(COMPANY, "A-1");
        Machine c = savedMachine(COMPANY, "C-3");
        savedMachine(OTHER_COMPANY, "A-0");
        inTx(() -> {
            Machine machine = machines.findByIdForUpdate(c.id()).orElseThrow();
            machine.changeStatus(MachineStatus.INACTIVE, OPERATOR, START);
            machines.save(machine);
        });

        assertThat(inTx(() -> machines.findByCompany(COMPANY, null))).extracting(Machine::id).containsExactly(a.id(), b.id(), c.id());
        assertThat(inTx(() -> machines.findByCompany(COMPANY, MachineStatus.ACTIVE))).extracting(Machine::id).containsExactly(a.id(), b.id());
        assertThat(inTx(() -> machines.findByCompany(COMPANY, MachineStatus.INACTIVE))).extracting(Machine::id).containsExactly(c.id());
        assertThat(inTx(() -> machines.findByCompany(COMPANY, MachineStatus.OUT_OF_SERVICE))).isEmpty();
        assertThat(inTx(() -> machines.findById(UUID.randomUUID()))).isEmpty();
    }
}
