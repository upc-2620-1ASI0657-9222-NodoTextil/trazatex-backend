# language: es
Característica: US21 — Iniciar transferencia de lotes
  Como operario, deseo iniciar una transferencia de lotes completos hacia otra empresa para continuar su recorrido dentro de la cadena.

  Escenario: 1: Inicio exitoso
    Dado que existen lotes habilitados
    Y selecciona una empresa destinataria de la misma cadena
    Cuando confirma la transferencia
    Entonces el sistema registra fecha y hora de inicio
    Y cambia los lotes a En transferencia
    Y alerta a los administradores receptores.

  Escenario: 2: Transferencia parcial
    Dado que se desea transferir solamente parte de un lote
    Cuando intenta iniciar la transferencia
    Entonces el sistema no lo permite
    Y requiere realizar previamente una división.
