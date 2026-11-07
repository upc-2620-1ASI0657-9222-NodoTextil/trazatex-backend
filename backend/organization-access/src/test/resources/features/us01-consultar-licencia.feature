# language: es
Característica: US01 — Consultar licencia
  Como dueño de licencia, deseo consultar las condiciones y uso de mi licencia para conocer la capacidad disponible de la cadena.

  Escenario: 1: Consulta de licencia
    Dado que el dueño de licencia ha iniciado sesión
    Cuando consulta la información de su licencia
    Entonces el sistema muestra las condiciones y límites contratados
    Y muestra las empresas utilizadas y usuarios registrados por empresa.
