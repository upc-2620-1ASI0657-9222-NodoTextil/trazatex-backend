package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.BatchQualityResult;
import com.nodotextil.trazatex.quality.application.dto.ControlResult;
import com.nodotextil.trazatex.quality.application.dto.FailureResult;
import com.nodotextil.trazatex.quality.application.port.in.ListQualityUseCase;
import com.nodotextil.trazatex.quality.application.port.out.BatchProjectionRepositoryPort;
import com.nodotextil.trazatex.quality.application.port.out.QualityControlRepositoryPort;
import com.nodotextil.trazatex.quality.application.port.out.QualityFailureRepositoryPort;
import com.nodotextil.trazatex.sharedkernel.paging.PageRequest;
import com.nodotextil.trazatex.sharedkernel.paging.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Controles, fallas y estado de calidad de los lotes de la empresa del solicitante, una página por consulta. */
@Service
class QualityListingService implements ListQualityUseCase {

    private final QualityControlRepositoryPort controls;
    private final QualityFailureRepositoryPort failures;
    private final BatchProjectionRepositoryPort batches;

    QualityListingService(QualityControlRepositoryPort controls, QualityFailureRepositoryPort failures, BatchProjectionRepositoryPort batches) {
        this.controls = controls;
        this.failures = failures;
        this.batches = batches;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ControlResult> controls(Integer page, Integer size, Actor actor) {
        return controls.findPageByCompany(actor.companyId(), PageRequest.of(page, size)).map(ControlResult::of);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<FailureResult> failures(Integer page, Integer size, Actor actor) {
        return failures.findPageByCompany(actor.companyId(), PageRequest.of(page, size)).map(FailureResult::of);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<BatchQualityResult> batches(Integer page, Integer size, Actor actor) {
        return batches.findQualityPageByCompany(actor.companyId(), PageRequest.of(page, size)).map(BatchQualityResult::of);
    }
}
