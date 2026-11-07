# language: es
Característica: US13 — Buscar lotes
  Como usuario autorizado, deseo buscar y filtrar lotes para localizar aquellos que necesito consultar o gestionar.

  Escenario: 1: Búsqueda de lote
    Dado que existen lotes registrados
    Cuando el usuario realiza una búsqueda o aplica filtros
    Entonces el sistema muestra los lotes coincidentes
    Y permite acceder a la información autorizada de cada lote.
