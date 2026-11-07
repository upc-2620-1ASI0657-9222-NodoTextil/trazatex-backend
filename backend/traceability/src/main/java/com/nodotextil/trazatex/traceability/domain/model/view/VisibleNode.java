package com.nodotextil.trazatex.traceability.domain.model.view;

import java.util.UUID;

/** Un nodo de la respuesta: un lote o una transformación. Los campos que la política no deja ver quedan nulos (y no salen en el JSON). */
public sealed interface VisibleNode extends VisibleView permits VisibleBatch, VisibleTransformation {

    UUID id();

    NodeKind kind();

    int level();

    Access access();
}
