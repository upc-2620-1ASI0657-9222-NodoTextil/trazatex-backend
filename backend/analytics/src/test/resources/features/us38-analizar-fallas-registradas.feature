# language: es
Característica: US38 — Analizar fallas registradas
  Como administrador de empresa, deseo analizar las fallas registradas para conocer su cantidad y causas más frecuentes.

  Escenario: 1: Análisis de fallas
    Dado que existen fallas dentro del periodo seleccionado
    Cuando el administrador consulta el análisis
    Entonces el sistema muestra la cantidad de fallas y sus causas más frecuentes
    Y presenta los resultados del periodo.
