# language: es
Característica: US27 — Descartar lote con falla
  Como administrador de empresa, deseo descartar un lote con falla para retirarlo definitivamente de las operaciones productivas.

  Escenario: 1: Descarte de lote
    Dado que existe un lote Con falla
    Cuando el administrador decide descartarlo
    Entonces el sistema cambia su fase a Descartado
    Y registra la Falla como Confirmada.
