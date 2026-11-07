# language: es
Característica: US35 — Recibir alerta por posible falla derivada
  Como administrador de empresa, deseo recibir una alerta cuando un lote de mi empresa sea identificado como posible falla derivada para revisarlo oportunamente.

  Escenario: 1: Generación de alerta
    Dado que un lote cambia a Posible falla derivada
    Cuando el sistema determina su posible relación con una Falla Confirmada
    Entonces genera una alerta para los administradores responsables
    Y permite identificar el lote que requiere revisión.
