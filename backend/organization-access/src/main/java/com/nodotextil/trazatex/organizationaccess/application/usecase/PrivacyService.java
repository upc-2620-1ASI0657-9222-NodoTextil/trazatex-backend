package com.nodotextil.trazatex.organizationaccess.application.usecase;

import com.nodotextil.trazatex.organizationaccess.application.dto.Actor;
import com.nodotextil.trazatex.organizationaccess.application.dto.PrivacyResult;
import com.nodotextil.trazatex.organizationaccess.application.port.in.ManagePrivacyUseCase;
import com.nodotextil.trazatex.organizationaccess.application.port.out.OrganizationEventPublisherPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.PrivacyRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.domain.event.CompanyPrivacyUpdated;
import com.nodotextil.trazatex.organizationaccess.domain.model.CompanyPrivacy;
import com.nodotextil.trazatex.organizationaccess.domain.model.Visibility;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Transactional
class PrivacyService implements ManagePrivacyUseCase {

    private final PrivacyRepositoryPort privacy;
    private final CompanyGuard companyGuard;
    private final OrganizationEventPublisherPort events;

    PrivacyService(PrivacyRepositoryPort privacy, CompanyGuard companyGuard, OrganizationEventPublisherPort events) {
        this.privacy = privacy;
        this.companyGuard = companyGuard;
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public PrivacyResult get(Actor actor) {
        return PrivacyResult.from(privacy.find(actor.companyId()));
    }

    @Override
    public PrivacyResult update(Map<String, Visibility> changes, Actor actor) {
        companyGuard.lockActive(actor.companyId());
        CompanyPrivacy current = privacy.find(actor.companyId());
        current.update(changes);
        privacy.save(current);
        events.publish(CompanyPrivacyUpdated.of(current));
        return PrivacyResult.from(current);
    }
}
