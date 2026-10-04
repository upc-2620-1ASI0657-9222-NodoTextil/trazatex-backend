package com.nodotextil.trazatex.quality.infrastructure.config;

import com.nodotextil.trazatex.quality.application.FinishQualityControlUseCase;
import com.nodotextil.trazatex.quality.application.ManageFailureUseCase;
import com.nodotextil.trazatex.quality.application.MarkPotentialDerivedFailureUseCase;
import com.nodotextil.trazatex.quality.application.RegisterQualityTestUseCase;
import com.nodotextil.trazatex.quality.application.StartQualityControlUseCase;
import com.nodotextil.trazatex.quality.application.UploadQualityEvidenceUseCase;
import com.nodotextil.trazatex.quality.application.contract.ProductionQualityPort;
import com.nodotextil.trazatex.quality.application.contract.QualityEventPublisher;
import com.nodotextil.trazatex.quality.application.contract.QualityEvidenceStorage;
import com.nodotextil.trazatex.quality.application.port.BatchQualityRepository;
import com.nodotextil.trazatex.quality.application.port.FailureRepository;
import com.nodotextil.trazatex.quality.application.port.QualityControlRepository;
import com.nodotextil.trazatex.quality.application.port.QualityEvidenceRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class QualityConfiguration {

    @Bean
    StartQualityControlUseCase startQualityControlUseCase(
            QualityControlRepository controls,
            BatchQualityRepository batchQuality,
            ObjectProvider<ProductionQualityPort> productionPorts) {

        return new StartQualityControlUseCase(
                controls,
                batchQuality,
                productionPort(productionPorts)
        );
    }

    @Bean
    RegisterQualityTestUseCase registerQualityTestUseCase(
            QualityControlRepository controls) {

        return new RegisterQualityTestUseCase(controls);
    }

    @Bean
    FinishQualityControlUseCase finishQualityControlUseCase(
            QualityControlRepository controls,
            BatchQualityRepository batchQuality,
            FailureRepository failures,
            ObjectProvider<ProductionQualityPort> productionPorts) {

        return new FinishQualityControlUseCase(
                controls,
                batchQuality,
                failures,
                productionPort(productionPorts)
        );
    }

    @Bean
    ManageFailureUseCase manageFailureUseCase(
            FailureRepository failures,
            BatchQualityRepository batchQuality,
            ObjectProvider<ProductionQualityPort> productionPorts,
            QualityEventPublisher eventPublisher) {

        return new ManageFailureUseCase(
                failures,
                batchQuality,
                productionPort(productionPorts),
                eventPublisher
        );
    }

    @Bean
    MarkPotentialDerivedFailureUseCase markPotentialDerivedFailureUseCase(
            BatchQualityRepository batchQuality,
            ObjectProvider<ProductionQualityPort> productionPorts) {

        return new MarkPotentialDerivedFailureUseCase(
                batchQuality,
                productionPort(productionPorts)
        );
    }

    @Bean
    UploadQualityEvidenceUseCase uploadQualityEvidenceUseCase(
            QualityControlRepository controls,
            FailureRepository failures,
            QualityEvidenceRepository evidence,
            QualityEvidenceStorage storage) {

        return new UploadQualityEvidenceUseCase(
                controls,
                failures,
                evidence,
                storage
        );
    }

    private ProductionQualityPort productionPort(
            ObjectProvider<ProductionQualityPort> productionPorts) {

        return productionPorts.getIfAvailable(MissingProductionQualityPort::new);
    }

    private static final class MissingProductionQualityPort
            implements ProductionQualityPort {

        private IllegalStateException missingIntegration() {
            return new IllegalStateException(
                    "Production integration is not available yet"
            );
        }

        @Override
        public void startQualityControl(UUID batchId) {
            throw missingIntegration();
        }

        @Override
        public void finishQualityControl(UUID batchId) {
            throw missingIntegration();
        }

        @Override
        public void blockBatch(UUID batchId) {
            throw missingIntegration();
        }

        @Override
        public void unblockBatch(UUID batchId) {
            throw missingIntegration();
        }

        @Override
        public void discardBatch(UUID batchId) {
            throw missingIntegration();
        }
    }
}
