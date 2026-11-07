# language: es
Característica: US31 — Consultar trazabilidad hacia atrás
  Como usuario autorizado, deseo consultar los antecedentes de un lote para conocer su origen dentro de la cadena textil.

  Escenario: 1: Consulta hacia atrás
    Dado que existe un lote con antecedentes registrados
    Cuando consulta su trazabilidad hacia atrás
    Entonces el sistema muestra los lotes y relaciones que dieron origen al lote
    Y respeta las reglas de privacidad de las empresas involucradas.
