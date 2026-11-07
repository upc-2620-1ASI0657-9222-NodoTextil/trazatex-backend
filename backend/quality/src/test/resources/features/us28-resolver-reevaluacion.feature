# language: es
Característica: US28 — Resolver reevaluación
  Como administrador de empresa, deseo finalizar la reevaluación de un lote para determinar si puede volver a operar o mantiene su falla.

  Escenario: 1: Reevaluación conforme
    Dado que todas las pruebas son Conformes
    Cuando finaliza el control
    Entonces el lote cambia a Conforme y queda desbloqueado
    Y la Falla se registra como Resuelta.

  Escenario: 2: Reevaluación con falla
    Dado que una o más pruebas vuelven a fallar
    Cuando finaliza el control
    Entonces el lote vuelve a Con falla
    Y queda pendiente de una nueva decisión administrativa.
