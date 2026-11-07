package com.nodotextil.trazatex.production.application.usecase;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.PageResult;
import com.nodotextil.trazatex.production.application.dto.RecipientResult;
import com.nodotextil.trazatex.production.application.port.in.ListTransferRecipientsUseCase;
import com.nodotextil.trazatex.production.application.port.out.CompanyProjectionRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TransferRecipientService implements ListTransferRecipientsUseCase {

    private final CompanyProjectionRepositoryPort companies;

    TransferRecipientService(CompanyProjectionRepositoryPort companies) {
        this.companies = companies;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<RecipientResult> list(Integer page, Integer size, Actor actor) {
        return PageResult.of(companies.findActive(Paging.page(page), Paging.size(size)).map(RecipientResult::from));
    }
}
