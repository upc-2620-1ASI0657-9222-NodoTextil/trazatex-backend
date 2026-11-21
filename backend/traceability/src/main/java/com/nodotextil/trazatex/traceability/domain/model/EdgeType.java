package com.nodotextil.trazatex.traceability.domain.model;

public enum EdgeType {
    OWNS_BATCH(false),
    INPUT_OF(true),
    PRODUCED(true),
    SPLIT_INTO(true),
    TRANSFERRED_VIA(false);

    private final boolean genealogical;

    EdgeType(boolean genealogical) {
        this.genealogical = genealogical;
    }

    public boolean isGenealogical() {
        return genealogical;
    }
}
