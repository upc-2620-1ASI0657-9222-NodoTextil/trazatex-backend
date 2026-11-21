package com.nodotextil.trazatex.traceability.domain.model.view;

import java.util.UUID;

public sealed interface VisibleNode extends VisibleView permits VisibleBatch, VisibleTransformation {

    UUID id();

    NodeKind kind();

    int level();

    Access access();
}
