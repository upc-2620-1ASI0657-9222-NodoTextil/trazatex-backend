package com.nodotextil.trazatex.quality.application.port.in;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.BatchQualityResult;
import com.nodotextil.trazatex.quality.application.dto.ControlResult;
import com.nodotextil.trazatex.quality.application.dto.FailureResult;
import com.nodotextil.trazatex.sharedkernel.paging.PageResult;

/**
 * Los listados de la propia empresa (siempre la del {@code Actor}), paginados (RNF-03): {@code page} desde 0, {@code size} por defecto 20 y
 * máximo 100; {@code 400 INVALID_PAGINATION} si no tienen sentido.
 */
public interface ListQualityUseCase {

    PageResult<ControlResult> controls(Integer page, Integer size, Actor actor);

    PageResult<FailureResult> failures(Integer page, Integer size, Actor actor);

    PageResult<BatchQualityResult> batches(Integer page, Integer size, Actor actor);
}
