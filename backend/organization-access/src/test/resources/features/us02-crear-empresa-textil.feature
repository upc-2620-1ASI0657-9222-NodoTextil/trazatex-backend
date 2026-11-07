# language: es
Característica: US02 — Crear empresa textil
  Como dueño de licencia, deseo crear una empresa textil para incorporarla a la cadena de trazabilidad.

  Escenario: 1: Creación exitosa de empresa
    Dado que existe capacidad disponible en la licencia
    Y el dueño completa la razón social, identificador empresarial, actividades textiles y correo del primer administrador
    Cuando confirma la creación
    Entonces el sistema registra la empresa dentro de la cadena
    Y envía una invitación al primer administrador.

  Escenario: 2: Límite de empresas alcanzado
    Dado que se alcanzó la cantidad máxima de empresas permitidas
    Cuando intenta crear una nueva empresa
    Entonces el sistema no realiza la creación
    Y comunica que se alcanzó el límite contratado.

  Escenario: 3: RUC inválido
    Dado que el identificador empresarial no corresponde a un contribuyente Activo y Habido
    Cuando el dueño de licencia intenta crear la empresa
    Entonces el sistema no realiza la creación
    Y comunica que el RUC no es válido.
