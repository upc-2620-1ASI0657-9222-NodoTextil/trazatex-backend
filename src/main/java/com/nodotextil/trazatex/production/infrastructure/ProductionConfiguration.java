package com.nodotextil.trazatex.production.infrastructure;

import com.nodotextil.trazatex.production.application.ChangeMachineStatusUseCase;
import com.nodotextil.trazatex.production.application.GetBatchUseCase;
import com.nodotextil.trazatex.production.application.GetMachineUseCase;
import com.nodotextil.trazatex.production.application.RegisterBatchUseCase;
import com.nodotextil.trazatex.production.application.RegisterMachineUseCase;
import com.nodotextil.trazatex.production.application.SplitBatchUseCase;
import com.nodotextil.trazatex.production.application.event.BatchEventPublisher;
import com.nodotextil.trazatex.production.domain.BatchRepository;
import com.nodotextil.trazatex.production.domain.MachineRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductionConfiguration {

    @Bean
    RegisterBatchUseCase registerBatchUseCase(BatchRepository batchRepository) {
        return new RegisterBatchUseCase(batchRepository, Clock.systemUTC());
    }

    @Bean
    GetBatchUseCase getBatchUseCase(BatchRepository batchRepository) {
        return new GetBatchUseCase(batchRepository);
    }

    @Bean
    SplitBatchUseCase splitBatchUseCase(
            BatchRepository batchRepository,
            BatchEventPublisher eventPublisher) {
        return new SplitBatchUseCase(batchRepository, eventPublisher, Clock.systemUTC());
    }

    @Bean
    RegisterMachineUseCase registerMachineUseCase(MachineRepository machineRepository) {
        return new RegisterMachineUseCase(machineRepository);
    }

    @Bean
    GetMachineUseCase getMachineUseCase(MachineRepository machineRepository) {
        return new GetMachineUseCase(machineRepository);
    }

    @Bean
    ChangeMachineStatusUseCase changeMachineStatusUseCase(MachineRepository machineRepository) {
        return new ChangeMachineStatusUseCase(machineRepository);
    }
}
