package com.nodotextil.trazatex.production.application;

import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.BatchRepository;

public class GetBatchByQrCodeUseCase {

    private final BatchRepository batchRepository;

    public GetBatchByQrCodeUseCase(BatchRepository batchRepository) {
        this.batchRepository = batchRepository;
    }

    public Batch execute(String qrCode) {
        return batchRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new BatchNotFoundException(qrCode));
    }
}
