package com.nodotextil.trazatex.production.application.port.out;

import com.nodotextil.trazatex.production.domain.model.TransferStatus;

import java.util.UUID;

/** Transferencias en las que participa una empresa, de la más reciente a la más antigua. */
public record TransferSearchCriteria(
        UUID companyId,
        Direction direction,
        TransferStatus status,
        int page,
        int size) {

    /** {@code size} se limita a {@link Page#MAX_SIZE}: ninguna consulta devuelve más de 100 filas por página. */
    public TransferSearchCriteria {
        if (page < 0) {
            throw new IllegalArgumentException("page debe ser >= 0: " + page);
        }
        if (size < 1) {
            throw new IllegalArgumentException("size debe ser >= 1: " + size);
        }
        size = Math.min(size, Page.MAX_SIZE);
    }

    /** {@code OUTGOING}: las que la empresa envía; {@code INCOMING}: las que recibe; {@code ALL}: ambas. */
    public enum Direction {
        OUTGOING,
        INCOMING,
        ALL
    }
}
