package com.nodotextil.trazatex.organizationaccess.application.usecase;

import com.nodotextil.trazatex.organizationaccess.application.dto.LicenseResult;
import com.nodotextil.trazatex.organizationaccess.application.port.in.ConsultLicenseUseCase;
import com.nodotextil.trazatex.organizationaccess.application.port.out.CompanyRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.LicenseRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.domain.model.Company;
import com.nodotextil.trazatex.organizationaccess.domain.model.License;
import com.nodotextil.trazatex.organizationaccess.domain.model.LicenseUsage;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
class LicenseService implements ConsultLicenseUseCase {

    private final LicenseRepositoryPort licenses;
    private final CompanyRepositoryPort companies;
    private final SlotCounter slots;
    private final Clock clock;

    LicenseService(LicenseRepositoryPort licenses, CompanyRepositoryPort companies, SlotCounter slots, Clock clock) {
        this.licenses = licenses;
        this.companies = companies;
        this.slots = slots;
        this.clock = clock;
    }

    @Override
    public LicenseResult getLicense() {
        License license = licenses.find().orElseThrow(() -> new NotFoundException("LICENSE_NOT_FOUND", "La licencia no está inicializada."));
        Instant now = clock.instant();
        List<Company> all = companies.findAll();
        List<LicenseUsage.CompanyUsage> perCompany = all.stream()
                .map(c -> new LicenseUsage.CompanyUsage(c.id(), c.businessName(), slots.usedSlots(c.id(), now), license.maxUsersPerCompany()))
                .toList();
        return LicenseResult.from(license.usage(all.size(), perCompany));
    }
}
