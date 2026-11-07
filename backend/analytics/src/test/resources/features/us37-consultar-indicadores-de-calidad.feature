# language: es
Característica: US37 — Consultar indicadores de calidad
  Como administrador de empresa, deseo consultar indicadores de calidad para conocer la situación de los lotes de mi empresa.

  Escenario: 1: Consulta por periodo
    Dado que existen registros dentro del periodo seleccionado
    Cuando el administrador consulta los indicadores
    Entonces el sistema muestra cantidades y porcentajes de lotes por estado de calidad
    Y presenta los resultados del periodo seleccionado.
