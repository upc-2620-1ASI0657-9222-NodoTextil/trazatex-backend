# language: es
Característica: US29 — Generar genealogía
  Como usuario de TrazaTex, deseo que el sistema genere automáticamente la genealogía de los lotes para conservar sus relaciones dentro de la cadena textil.

  Escenario: 1: Relación por división
    Dado que un lote ha sido dividido
    Cuando se generan sus hijos
    Entonces el sistema registra la relación padre-hijo.

  Escenario: 2: Relación por transformación
    Dado que una transformación genera lotes de salida
    Cuando finaliza correctamente
    Entonces relaciona todas sus entradas con sus salidas.

  Escenario: 3: Transferencia
    Dado que un lote es transferido entre empresas
    Cuando se resuelve la transferencia
    Entonces el sistema conserva la transferencia dentro del historial del mismo lote
    Y no crea un nuevo lote ni una relación genealógica padre-hijo.
