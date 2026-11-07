package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.application.dto.CachedRucValidation;
import com.nodotextil.trazatex.organizationaccess.application.port.out.RucValidationCachePort;
import com.nodotextil.trazatex.organizationaccess.domain.model.Ruc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Optional;

@Component
class JdbcRucValidationCacheAdapter implements RucValidationCachePort {

    private static final int MAX_NAME = 300;
    private static final int MAX_STATUS = 40;

    private final JdbcClient jdbc;

    JdbcRucValidationCacheAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CachedRucValidation> find(Ruc ruc) {
        return jdbc.sql("SELECT business_name, taxpayer_status, domicile_condition, validated_at FROM validated_ruc_cache WHERE ruc = :ruc")
                .param("ruc", ruc.value())
                .query((rs, row) -> new CachedRucValidation(ruc, rs.getString("business_name"), rs.getString("taxpayer_status"),
                        rs.getString("domicile_condition"), Timestamps.fromDb(rs.getObject("validated_at", OffsetDateTime.class))))
                .optional();
    }

    @Override
    public void save(CachedRucValidation validation) {
        jdbc.sql("""
                        INSERT INTO validated_ruc_cache (ruc, business_name, taxpayer_status, domicile_condition, validated_at)
                        VALUES (:ruc, :name, :status, :condition, :at)
                        ON CONFLICT (ruc) DO UPDATE SET business_name = EXCLUDED.business_name,
                            taxpayer_status = EXCLUDED.taxpayer_status, domicile_condition = EXCLUDED.domicile_condition,
                            validated_at = EXCLUDED.validated_at
                        """)
                .param("ruc", validation.ruc().value())
                .param("name", cut(validation.businessName(), MAX_NAME))
                .param("status", cut(validation.taxpayerStatus(), MAX_STATUS))
                .param("condition", cut(validation.domicileCondition(), MAX_STATUS))
                .param("at", Timestamps.toDb(validation.validatedAt()))
                .update();
    }

    @Override
    public void delete(Ruc ruc) {
        jdbc.sql("DELETE FROM validated_ruc_cache WHERE ruc = :ruc").param("ruc", ruc.value()).update();
    }

    private static String cut(String value, int max) {
        String text = value == null ? "" : value;
        return text.length() <= max ? text : text.substring(0, max);
    }
}
