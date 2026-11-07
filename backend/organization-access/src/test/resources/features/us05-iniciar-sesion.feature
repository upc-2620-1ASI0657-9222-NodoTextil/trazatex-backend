# language: es
Característica: US05 — Iniciar sesión
  Como usuario de TrazaTex, deseo iniciar sesión para acceder a las funcionalidades correspondientes a mi rol.

  Escenario: 1: Inicio de sesión exitoso
    Dado que el usuario posee una cuenta Activa
    Y proporciona su correo y contraseña correctos
    Cuando inicia sesión
    Entonces el sistema autentica al usuario
    Y permite el acceso según su rol.

  Escenario: 2: Credenciales incorrectas
    Dado que el usuario proporciona credenciales incorrectas
    Cuando intenta iniciar sesión
    Entonces el sistema no permite el acceso
    Y comunica que las credenciales no son válidas.
