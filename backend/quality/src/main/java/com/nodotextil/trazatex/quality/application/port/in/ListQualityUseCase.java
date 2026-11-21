package com.nodotextil.trazatex.quality.application.port.in;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.BatchQualityResult;
import com.nodotextil.trazatex.quality.application.dto.ControlResult;
import com.nodotextil.trazatex.quality.application.dto.FailureResult;
import com.nodotextil.trazatex.sharedkernel.paging.PageResult;

public interface ListQualityUseCase {

    PageResult<ControlResult> controls(Integer page, Integer size, Actor actor);

    PageResult<FailureResult> failures(Integer page, Integer size, Actor actor);

    PageResult<BatchQualityResult> batches(Integer page, Integer size, Actor actor);
}
