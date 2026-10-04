package com.nodotextil.trazatex.quality.infrastructure.config;

import com.nodotextil.trazatex.quality.application.FinishQualityControlUseCase;
import com.nodotextil.trazatex.quality.application.InitializeBatchQualityUseCase;
import com.nodotextil.trazatex.quality.application.ManageFailureUseCase;
import com.nodotextil.trazatex.quality.application.MarkPotentialDerivedFailureUseCase;
import com.nodotextil.trazatex.quality.application.QualityAccessService;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class QualityConfiguration {


    @Bean
    QualityAccessService qualityAccessService(
            QualityControlRepository controls,
            FailureRepository failures,
            ProductionQualityPort productionPort) {
        return new QualityAccessService(controls, failures, productionPort);
    }
    @Bean
    InitializeBatchQualityUseCase initializeBatchQualityUseCase(BatchQualityRepository batchQuality) {
        return new InitializeBatchQualityUseCase(batchQuality);
    }
    @Bean
    StartQualityControlUseCase startQualityControlUseCase(
            QualityControlRepository controls,
            BatchQualityRepository batchQuality,
            ProductionQualityPort productionPort) {

        return new StartQualityControlUseCase(
                controls,
                batchQuality,
                productionPort
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
            ProductionQualityPort productionPort) {

        return new FinishQualityControlUseCase(
                controls,
                batchQuality,
                failures,
                productionPort
        );
    }

    @Bean
    ManageFailureUseCase manageFailureUseCase(
            FailureRepository failures,
            BatchQualityRepository batchQuality,
            ProductionQualityPort productionPort,
            QualityEventPublisher eventPublisher) {

        return new ManageFailureUseCase(
                failures,
                batchQuality,
                productionPort,
                eventPublisher
        );
    }

    @Bean
    MarkPotentialDerivedFailureUseCase markPotentialDerivedFailureUseCase(
            BatchQualityRepository batchQuality,
            ProductionQualityPort productionPort) {

        return new MarkPotentialDerivedFailureUseCase(
                batchQuality,
                productionPort
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

}
