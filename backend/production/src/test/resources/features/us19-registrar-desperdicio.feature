# language: es
Característica: US19 — Registrar desperdicio
  Como operario, deseo registrar el desperdicio generado durante una transformación para conservar la cantidad perdida y su motivo.

  Escenario: 1: Transformación con desperdicio
    Dado que una transformación genera desperdicio
    Y el operario registra una cantidad mayor a cero
    Cuando completa la información
    Entonces el sistema solicita el motivo
    Y conserva cantidad y motivo como parte del balance.
