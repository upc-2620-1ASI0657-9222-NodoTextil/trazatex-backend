# language: es
Característica: US25 — Finalizar control de calidad
  Como administrador de empresa, deseo finalizar un control de calidad para determinar el estado de calidad del lote.

  Escenario: 1: Todas las pruebas conformes
    Dado que todas las pruebas son Conformes
    Cuando finaliza el control
    Entonces registra fecha y hora de finalización
    Y devuelve la fase del lote a Disponible
    Y establece su calidad como Conforme.

  Escenario: 2: Control con falla
    Dado que una o más pruebas tienen resultado Con falla
    Cuando finaliza el control
    Entonces devuelve la fase del lote a Disponible
    Y establece su calidad como Con falla
    Y genera automáticamente una Falla
    Y mantiene el lote bloqueado.
