# language: es
Característica: US24 — Registrar pruebas de calidad
  Como administrador de empresa, deseo registrar las pruebas realizadas durante un control para conservar los resultados obtenidos.

  Escenario: 1: Registro de prueba
    Dado que existe un control activo
    Y el administrador ingresa criterio, valor esperado, valor obtenido, unidad, resultado y observaciones
    Cuando confirma el registro
    Entonces el sistema conserva la prueba
    Y la asocia con el control correspondiente.
