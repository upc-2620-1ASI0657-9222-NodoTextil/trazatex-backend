# language: es
Característica: US32 — Consultar trazabilidad hacia adelante
  Como usuario autorizado, deseo consultar los descendientes de un lote para conocer los recorridos generados a partir de él.

  Escenario: 1: Consulta hacia adelante
    Dado que existe un lote con descendientes registrados
    Cuando consulta su trazabilidad hacia adelante
    Entonces el sistema muestra los lotes derivados y sus relaciones
    Y representa las ramificaciones existentes.
