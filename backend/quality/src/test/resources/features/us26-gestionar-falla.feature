# language: es
Característica: US26 — Gestionar falla
  Como administrador de empresa, deseo completar y gestionar una falla para registrar su información y decidir el tratamiento del lote afectado.

  Escenario: 1: Completar información
    Dado que existe una Falla generada por un control
    Cuando registra causa, cantidad afectada y observaciones
    Entonces el sistema conserva la información
    Y mantiene su relación con el lote, control y pruebas fallidas.

  Escenario: 2: Enviar a reevaluación
    Dado que existe un lote Con falla
    Cuando decide reevaluarlo
    Entonces el sistema cambia su calidad a Por reevaluar
    Y lo mantiene bloqueado hasta completar la reevaluación.
