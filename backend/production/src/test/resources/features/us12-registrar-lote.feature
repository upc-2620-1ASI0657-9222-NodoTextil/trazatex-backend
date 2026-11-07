# language: es
Característica: US12 — Registrar lote
  Como operario, deseo registrar un lote para conservar sus características e incorporarlo a la trazabilidad de la cadena textil.

  Escenario: 1: Registro exitoso
    Dado que el operario completa la información obligatoria
    Y registra una composición cuyos porcentajes suman 100 %
    Cuando confirma el registro
    Entonces el sistema crea el lote como Disponible y No revisado
    Y genera un Traceability ID y QR únicos.

  Escenario: 2: Proveedor desconocido
    Dado que el operario no conoce el nombre del proveedor
    Cuando registra el lote sin completar dicho campo
    Entonces el sistema permite continuar con el registro.

  Escenario: 3: Composición inválida
    Dado que los porcentajes no suman 100 %
    Cuando intenta registrar el lote
    Entonces el sistema no realiza el registro
    Y comunica que la composición no es válida.
