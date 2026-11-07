package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.domain.model.CompanyStatus;
import com.nodotextil.trazatex.organizationaccess.domain.model.TextileActivity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "companies")
class CompanyEntity {

    @Id
    UUID id;
    @Column(nullable = false)
    String ruc;
    @Column(name = "business_name", nullable = false)
    String businessName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    CompanyStatus status;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "company_activities", joinColumns = @JoinColumn(name = "company_id"))
    @Column(name = "activity", nullable = false)
    @Enumerated(EnumType.STRING)
    Set<TextileActivity> activities = EnumSet.noneOf(TextileActivity.class);

    protected CompanyEntity() {
    }
}
