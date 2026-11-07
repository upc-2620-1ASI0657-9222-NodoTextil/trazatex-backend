# language: es
Característica: US44 — Generar reporte de control de calidad
  Como administrador de empresa, deseo generar un reporte en PDF con los resultados de un control de calidad para compartirlo o conservarlo como evidencia documental.

  Escenario: 1: Generación exitosa
    Dado que existe un control de calidad finalizado
    Cuando el administrador solicita generar su reporte
    Entonces el sistema genera el documento con las pruebas, valores y resultados registrados
    Y no lo almacena dentro del sistema.

  Escenario: 2: Servicio no disponible
    Dado que el servicio de generación de PDF no responde
    Cuando el administrador intenta generar el reporte
    Entonces el sistema comunica el error
    Y permite reintentar.
