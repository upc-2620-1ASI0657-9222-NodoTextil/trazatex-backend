# language: es
Característica: US07 — Solicitar administrador
  Como administrador de empresa, deseo solicitar la incorporación de otro administrador para ampliar la gestión de mi empresa.

  Escenario: 1: Solicitud exitosa
    Dado que el administrador completa el nombre, correo y cargo del nuevo administrador
    Cuando confirma la solicitud
    Entonces el sistema registra la solicitud
    Y la envía al dueño de licencia para su revisión.

  Escenario: 2: Datos incompletos
    Dado que faltan datos obligatorios
    Cuando intenta enviar la solicitud
    Entonces el sistema no la registra
    Y señala los datos pendientes.
