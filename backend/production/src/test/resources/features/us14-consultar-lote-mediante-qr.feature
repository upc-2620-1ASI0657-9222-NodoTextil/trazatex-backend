# language: es
Característica: US14 — Consultar lote mediante QR
  Como usuario autorizado, deseo escanear el código QR de un lote para acceder rápidamente a su información disponible.

  Escenario: 1: QR válido
    Dado que existe un lote registrado con un código QR asociado
    Cuando el usuario escanea el código
    Entonces el sistema identifica el lote
    Y muestra la información autorizada.

  Escenario: 2: QR no reconocido
    Dado que el código no corresponde a un lote registrado
    Cuando el usuario realiza el escaneo
    Entonces el sistema no muestra información
    Y comunica que el código no fue encontrado.
