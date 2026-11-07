# language: es
Característica: US30 — Buscar genealogías
  Como usuario autorizado, deseo buscar genealogías para localizar la trazabilidad que necesito consultar.

  Escenario: 1: Búsqueda de genealogía
    Dado que existen genealogías registradas
    Cuando el usuario realiza una búsqueda
    Entonces el sistema muestra las genealogías coincidentes
    Y permite acceder a aquellas que puede consultar.
