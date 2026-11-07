package com.nodotextil.trazatex.production.application.usecase;

import com.nodotextil.trazatex.production.application.port.in.SyncCompanyProjectionUseCase;
import com.nodotextil.trazatex.production.application.port.out.CompanyProjectionRepositoryPort;
import com.nodotextil.trazatex.production.domain.model.CompanyProjection;
import com.nodotextil.trazatex.production.domain.model.CompanyStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Aplica los eventos de empresa a la proyección. Los cambios de una misma empresa se serializan con el bloqueo de su fila. El
 * estado lo manda siempre el {@code CompanyStatusChanged} más reciente: un {@code CompanyRegistered} sobre una empresa que ya
 * se conocía no lo pisa.
 */
@Service
@Transactional
class CompanyProjectionService implements SyncCompanyProjectionUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompanyProjectionService.class);

    private final CompanyProjectionRepositoryPort companies;
    private final Clock clock;

    CompanyProjectionService(CompanyProjectionRepositoryPort companies, Clock clock) {
        this.companies = companies;
        this.clock = clock;
    }

    @Override
    public void registered(UUID companyId, String businessName, CompanyStatus status) {
        companies.findByIdForUpdate(companyId).ifPresentOrElse(existing -> {
            if (!Objects.equals(existing.businessName(), businessName)) {
                existing.rename(businessName, clock.instant());
                companies.save(existing);
            }
        }, () -> companies.save(CompanyProjection.of(companyId, businessName, status, clock.instant())));
    }

    @Override
    public void statusChanged(UUID companyId, CompanyStatus status) {
        companies.findByIdForUpdate(companyId).ifPresentOrElse(existing -> {
            if (existing.status() != status) {
                existing.changeStatus(status, clock.instant());
                companies.save(existing);
            }
        }, () -> {
            log.warn("CompanyStatusChanged de la empresa {} que la proyección no conocía: se crea con estado {} (su alta no llegó)",
                    companyId, status);
            companies.save(CompanyProjection.of(companyId, null, status, clock.instant()));
        });
    }
}
