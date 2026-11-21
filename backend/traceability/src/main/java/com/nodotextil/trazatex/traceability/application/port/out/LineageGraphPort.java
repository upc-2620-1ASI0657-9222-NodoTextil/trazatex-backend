package com.nodotextil.trazatex.traceability.application.port.out;

import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchPhaseChangedCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchSplitCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyCompanyRegisteredCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyQualityStatusCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyTransferAcceptedCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyTransformationCompletedCommand;
import com.nodotextil.trazatex.traceability.application.dto.RegisterBatchCommand;
import com.nodotextil.trazatex.traceability.domain.model.CompanyPrivacy;

import java.util.UUID;

public interface LineageGraphPort {

    void registerBatch(RegisterBatchCommand command);

    void applyBatchSplit(ApplyBatchSplitCommand command);

    void applyTransformationCompleted(ApplyTransformationCompletedCommand command);

    boolean applyBatchPhaseChanged(ApplyBatchPhaseChangedCommand command);

    void applyTransferAccepted(ApplyTransferAcceptedCommand command);

    void applyQualityStatus(ApplyQualityStatusCommand command);

    void saveCompanyPrivacy(UUID companyId, CompanyPrivacy privacy);

    void registerCompany(ApplyCompanyRegisteredCommand command);
}
