# language: es
Característica: US40 — Consultar pérdidas de material
  Como administrador de empresa, deseo consultar la merma y desperdicio acumulados para analizar las pérdidas registradas en los procesos productivos.

  Escenario: 1: Consulta de pérdidas
    Dado que existen transformaciones finalizadas dentro del periodo seleccionado
    Cuando el administrador consulta los indicadores de material
    Entonces el sistema muestra la merma acumulada y el desperdicio acumulado
    Y presenta los resultados del periodo.
