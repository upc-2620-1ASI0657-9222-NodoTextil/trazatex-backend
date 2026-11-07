package com.nodotextil.trazatex.production.application.port.in;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.BatchResult;
import com.nodotextil.trazatex.production.application.dto.BatchSummaryResult;
import com.nodotextil.trazatex.production.application.dto.CommercialDataInput;
import com.nodotextil.trazatex.production.application.dto.PageResult;
import com.nodotextil.trazatex.production.application.dto.QrLookupResult;
import com.nodotextil.trazatex.production.application.dto.RegisterBatchCommand;
import com.nodotextil.trazatex.production.application.dto.SearchBatchesQuery;
import com.nodotextil.trazatex.production.application.dto.SplitBatchCommand;
import com.nodotextil.trazatex.production.application.dto.SplitResult;

import java.util.UUID;

/** US12 registrar, US13 buscar, US14 consultar por QR, US15 dividir, US20 datos comerciales. */
public interface ManageBatchesUseCase {

    BatchResult register(RegisterBatchCommand command, Actor actor);

    PageResult<BatchSummaryResult> search(SearchBatchesQuery query, Actor actor);

    BatchResult get(UUID batchId, Actor actor);

    QrLookupResult findByQr(String qrCode, Actor actor);

    SplitResult split(UUID batchId, SplitBatchCommand command, Actor actor);

    BatchResult updateCommercialData(UUID batchId, CommercialDataInput data, Actor actor);
}
