package com.nodotextil.trazatex.quality.application.dto;

import com.nodotextil.trazatex.quality.domain.model.ControlType;
import com.nodotextil.trazatex.quality.domain.model.TestResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lo que dice el reporte de un control finalizado (US44). Es un documento para <b>compartir</b>: lleva las pruebas con sus valores, resultados y
 * observaciones, y NO lleva las observaciones internas del control, nada de la falla (causa, cantidad, observaciones) ni la identidad de quien
 * hizo o finalizó el control. Por construcción: este tipo no tiene esos datos.
 *
 * @param batchReference el Traceability ID del lote o, en las filas anteriores a V4 que aún no lo tienen, su UUID
 */
public record ControlReport(UUID controlId, String batchReference, ControlType controlType, Instant startedAt, Instant finishedAt, TestResult result,
                            List<Test> tests, Instant generatedAt) {

    public record Test(String criterion, String expectedValue, String obtainedValue, String unit, TestResult result, String remarks) {
    }
}
