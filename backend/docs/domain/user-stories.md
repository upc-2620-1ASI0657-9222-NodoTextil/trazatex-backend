# Historias de usuario y criterios de aceptación

Copiadas del informe (Tabla 17). Cada escenario se convierte en un escenario Gherkin ejecutable (RNF-10) en `src/test/resources/features/` del servicio dueño.

Servicio dueño por épica: EP01 → organization-access, EP02 → production, EP03 → quality, EP04 → traceability (US34 la ejecuta quality; US35, notification), EP05 → analytics.


## EP01 — Gestión de empresas y recursos

### US01 — Consultar licencia

Como dueño de licencia, deseo consultar las condiciones y uso de mi licencia para conocer la capacidad disponible de la cadena.

```gherkin
Escenario 1: Consulta de licencia
  Dado que el dueño de licencia ha iniciado sesión
  Cuando consulta la información de su licencia
  Entonces el sistema muestra las condiciones y límites contratados
  Y muestra las empresas utilizadas y usuarios registrados por empresa.
```

### US02 — Crear empresa textil

Como dueño de licencia, deseo crear una empresa textil para incorporarla a la cadena de trazabilidad.

```gherkin
Escenario 1: Creación exitosa de empresa
  Dado que existe capacidad disponible en la licencia
  Y el dueño completa la razón social, identificador empresarial, actividades textiles y correo del primer administrador
  Cuando confirma la creación
  Entonces el sistema registra la empresa dentro de la cadena
  Y envía una invitación al primer administrador.
Escenario 2: Límite de empresas alcanzado
  Dado que se alcanzó la cantidad máxima de empresas permitidas
  Cuando intenta crear una nueva empresa
  Entonces el sistema no realiza la creación
  Y comunica que se alcanzó el límite contratado.
Escenario 3: RUC inválido
  Dado que el identificador empresarial no corresponde a un contribuyente Activo y Habido
  Cuando el dueño de licencia intenta crear la empresa
  Entonces el sistema no realiza la creación
  Y comunica que el RUC no es válido.
```

### US03 — Gestionar estado de empresa

Como dueño de licencia, deseo activar o inactivar una empresa para gestionar su participación en la cadena sin eliminar su historial.

```gherkin
Escenario 1: Inactivar empresa
  Dado que existe una empresa Activa
  Cuando el dueño de licencia decide inactivarla
  Entonces el sistema cambia su estado a Inactiva
  Y conserva toda su información e historial
  Y sus usuarios no pueden iniciar nuevas operaciones productivas.
Escenario 2: Activar empresa
  Dado que existe una empresa Inactiva
  Cuando el dueño de licencia decide activarla
  Entonces el sistema cambia su estado a Activa
  Y permite nuevamente las operaciones correspondientes a sus usuarios.
```

### US04 — Aceptar invitación

Como usuario invitado, deseo aceptar una invitación vigente para crear mi cuenta y quedar asociado a la empresa y rol asignados.

```gherkin
Escenario 1: Invitación válida
  Dado que el usuario posee una invitación Pendiente y vigente
  Y completa sus nombres, apellidos, contraseña y confirmación de contraseña
  Cuando acepta la invitación
  Entonces el sistema crea y activa su cuenta
  Y cambia la invitación a Aceptada.
Escenario 2: Invitación expirada
  Dado que han transcurrido 7 días desde el envío de una invitación Pendiente
  Cuando el usuario intenta aceptarla
  Entonces el sistema no crea la cuenta
  Y la invitación permanece Expirada.
Escenario 3: Invitación cancelada
  Dado que la invitación se encuentra Cancelada
  Cuando el usuario intenta aceptarla
  Entonces el sistema no permite utilizarla.
Escenario 4: Invitación ya utilizada
  Dado que la invitación se encuentra Aceptada
  Cuando se intenta utilizar nuevamente
  Entonces el sistema no permite crear otra cuenta con ella.
Escenario 5: Contraseña comprometida
  Dado que la contraseña elegida se encuentra en bases de datos de credenciales filtradas
  Cuando el usuario intenta aceptar la invitación
  Entonces el sistema rechaza la contraseña
  Y solicita una diferente.
```

### US05 — Iniciar sesión

Como usuario de TrazaTex, deseo iniciar sesión para acceder a las funcionalidades correspondientes a mi rol.

```gherkin
Escenario 1: Inicio de sesión exitoso
  Dado que el usuario posee una cuenta Activa
  Y proporciona su correo y contraseña correctos
  Cuando inicia sesión
  Entonces el sistema autentica al usuario
  Y permite el acceso según su rol.
Escenario 2: Credenciales incorrectas
  Dado que el usuario proporciona credenciales incorrectas
  Cuando intenta iniciar sesión
  Entonces el sistema no permite el acceso
  Y comunica que las credenciales no son válidas.
```

### US06 — Invitar operario

Como administrador de empresa, deseo invitar a un operario para incorporarlo como usuario de mi empresa.

```gherkin
Escenario 1: Invitación exitosa
  Dado que existe capacidad de usuarios disponible
  Y el administrador completa los datos del operario
  Cuando confirma la invitación
  Entonces el sistema genera una invitación Pendiente
  Y la envía al correo registrado.
Escenario 2: Límite de usuarios alcanzado
  Dado que la suma de usuarios Activos e invitaciones Pendientes alcanza el límite contratado
  Cuando el administrador intenta invitar a otro operario
  Entonces el sistema no genera la invitación
  Y comunica que no existe capacidad disponible.
```

### US07 — Solicitar administrador

Como administrador de empresa, deseo solicitar la incorporación de otro administrador para ampliar la gestión de mi empresa.

```gherkin
Escenario 1: Solicitud exitosa
  Dado que el administrador completa el nombre, correo y cargo del nuevo administrador
  Cuando confirma la solicitud
  Entonces el sistema registra la solicitud
  Y la envía al dueño de licencia para su revisión.
Escenario 2: Datos incompletos
  Dado que faltan datos obligatorios
  Cuando intenta enviar la solicitud
  Entonces el sistema no la registra
  Y señala los datos pendientes.
```

### US08 — Revisar solicitud de administrador

Como dueño de licencia, deseo revisar las solicitudes de nuevos administradores para aprobar o rechazar su incorporación.

```gherkin
Escenario 1: Solicitud aprobada con capacidad
  Dado que existe una solicitud Pendiente
  Y la empresa posee capacidad de usuarios disponible
  Cuando el dueño de licencia la aprueba
  Entonces el sistema genera una invitación Pendiente para el nuevo administrador.
Escenario 2: Sin capacidad disponible
  Dado que existe una solicitud Pendiente
  Y la empresa alcanzó su límite de usuarios
  Cuando el dueño intenta aprobarla
  Entonces el sistema no genera la invitación
  Y comunica que no existe capacidad disponible.
Escenario 3: Solicitud rechazada
  Dado que existe una solicitud Pendiente
  Cuando el dueño de licencia la rechaza
  Entonces el sistema registra el rechazo
  Y no genera una invitación.
```

### US09 — Gestionar estado de usuario

Como administrador de empresa, deseo gestionar el estado de los usuarios de mi empresa para controlar su acceso sin eliminar su historial.

```gherkin
Escenario 1: Inactivar usuario
  Dado que existe un usuario Activo
  Cuando el administrador lo inactiva
  Entonces cambia a Inactivo
  Y conserva su historial
  Y no puede autenticarse ni realizar operaciones.
Escenario 2: Reactivar usuario
  Dado que existe un usuario Inactivo
  Y existe capacidad disponible
  Cuando el administrador lo reactiva
  Entonces cambia a Activo
  Y recupera el acceso según su rol.
Escenario 3: Reactivación sin capacidad
  Dado que no existe capacidad disponible
  Cuando se intenta reactivar al usuario
  Entonces el sistema no permite la reactivación.
```

### US10 — Gestionar maquinaria

Como administrador de empresa, deseo registrar y gestionar las maquinarias de mi empresa para asociarlas con las transformaciones textiles.

```gherkin
Escenario 1: Registro de maquinaria
  Dado que el administrador completa código, nombre y tipo
  Cuando confirma el registro
  Entonces el sistema registra la maquinaria como Activa.
Escenario 2: Cambio de estado
  Dado que existe una maquinaria registrada
  Cuando cambia su estado
  Entonces el sistema permite establecerla como Activa, Fuera de servicio o Inactiva
  Y conserva su historial.
Escenario 3: Maquinaria no activa
  Dado que una maquinaria está Fuera de servicio o Inactiva
  Cuando un operario intenta utilizarla en una transformación
  Entonces el sistema no permite seleccionarla.
```

### US11 — Configurar privacidad B2B

Como administrador de empresa, deseo configurar la privacidad de los datos de mi empresa para controlar su visibilidad ante otras empresas.

```gherkin
Escenario 1: Configuración inicial
  Dado que existe un campo Configurable
  Cuando se habilita para la empresa
  Entonces el sistema lo establece como Privado por defecto.
Escenario 2: Compartir campo configurable
  Dado que existe un campo Configurable Privado
  Cuando el administrador lo establece como Compartido
  Entonces el sistema guarda la configuración
  Y permite visualizarlo desde otras empresas cuando corresponda.
Escenario 3: Volver privado un campo configurable
  Dado que existe un campo Configurable Compartido
  Cuando el administrador lo establece como Privado
  Entonces el sistema deja de mostrarlo a otras empresas.
Escenario 4: Información siempre compartida
  Dado que el administrador consulta Traceability ID, empresa responsable, relaciones de genealogía, tipo de material, fase operativa, estado de calidad, fecha de registro o indicador de producto final
  Cuando intenta establecerlo como Privado
  Entonces el sistema no permite modificar su visibilidad.
Escenario 5: Información siempre privada
  Dado que se consulta la identidad del operario, observaciones internas o datos comerciales de producto final
  Cuando otra empresa accede a la trazabilidad
  Entonces el sistema mantiene dicha información oculta.
```

### US41 — Cancelar invitación

Como usuario que emitió una invitación, deseo cancelarla mientras se encuentre Pendiente para impedir que pueda utilizarse cuando la incorporación ya no corresponda.

```gherkin
Escenario 1: Cancelación exitosa
  Dado que existe una invitación Pendiente emitida por el usuario
  Cuando decide cancelarla
  Entonces el sistema cambia su estado a Cancelada
  Y la invitación ya no puede utilizarse para crear una cuenta.
Escenario 2: Invitación no cancelable
  Dado que una invitación se encuentra Aceptada, Expirada o Cancelada
  Cuando el usuario intenta cancelarla
  Entonces el sistema no modifica su estado.
```


## EP02 — Gestión de lotes y transformaciones

### US12 — Registrar lote

Como operario, deseo registrar un lote para conservar sus características e incorporarlo a la trazabilidad de la cadena textil.

```gherkin
Escenario 1: Registro exitoso
  Dado que el operario completa la información obligatoria
  Y registra una composición cuyos porcentajes suman 100 %
  Cuando confirma el registro
  Entonces el sistema crea el lote como Disponible y No revisado
  Y genera un Traceability ID y QR únicos.
Escenario 2: Proveedor desconocido
  Dado que el operario no conoce el nombre del proveedor
  Cuando registra el lote sin completar dicho campo
  Entonces el sistema permite continuar con el registro.
Escenario 3: Composición inválida
  Dado que los porcentajes no suman 100 %
  Cuando intenta registrar el lote
  Entonces el sistema no realiza el registro
  Y comunica que la composición no es válida.
```

### US13 — Buscar lotes

Como usuario autorizado, deseo buscar y filtrar lotes para localizar aquellos que necesito consultar o gestionar.

```gherkin
Escenario 1: Búsqueda de lote
  Dado que existen lotes registrados
  Cuando el usuario realiza una búsqueda o aplica filtros
  Entonces el sistema muestra los lotes coincidentes
  Y permite acceder a la información autorizada de cada lote.
```

### US14 — Consultar lote mediante QR

Como usuario autorizado, deseo escanear el código QR de un lote para acceder rápidamente a su información disponible.

```gherkin
Escenario 1: QR válido
  Dado que existe un lote registrado con un código QR asociado
  Cuando el usuario escanea el código
  Entonces el sistema identifica el lote
  Y muestra la información autorizada.
Escenario 2: QR no reconocido
  Dado que el código no corresponde a un lote registrado
  Cuando el usuario realiza el escaneo
  Entonces el sistema no muestra información
  Y comunica que el código no fue encontrado.
```

### US15 — Dividir lote

Como operario, deseo dividir un lote para generar lotes hijos que puedan continuar recorridos productivos independientes.

```gherkin
Escenario 1: División exitosa
  Dado que existe un lote habilitado
  Y el operario registra dos o más lotes hijos cuya cantidad total equivale a la cantidad del lote padre
  Cuando confirma la división
  Entonces el lote padre cambia a Dividido
  Y los hijos nacen Disponibles con nuevos Traceability ID y QR.
Escenario 2: Cantidades inválidas
  Dado que la suma de los hijos no equivale a la cantidad del padre
  Cuando intenta confirmar la división
  Entonces el sistema no realiza la operación
  Y comunica que las cantidades no coinciden.
```

### US16 — Iniciar transformación textil

Como operario, deseo iniciar una transformación textil para registrar los lotes y recursos que participan en el proceso productivo.

```gherkin
Escenario 1: Inicio exitoso
  Dado que existen uno o más lotes habilitados
  Y el operario selecciona una maquinaria Activa y un tipo de transformación
  Cuando confirma el inicio
  Entonces el sistema registra fecha y hora de inicio
  Y cambia los lotes a En transformación.
Escenario 2: Lote no habilitado
  Dado que uno de los lotes seleccionados no está habilitado
  Cuando intenta iniciar la transformación
  Entonces el sistema no permite iniciarla.
Escenario 3: Maquinaria no disponible
  Dado que la maquinaria está Fuera de servicio o Inactiva
  Cuando intenta seleccionarla
  Entonces el sistema no permite utilizarla.
```

### US17 — Determinar composición de transformación

Como operario, deseo que el sistema determine la composición resultante de una transformación para conservar correctamente la información de los materiales procesados.

```gherkin
Escenario 1: Un lote de entrada
  Dado que la transformación utiliza un solo lote
  Cuando se generan sus lotes de salida
  Entonces el sistema conserva la composición del lote de entrada.
Escenario 2: Varios lotes de entrada
  Dado que la transformación utiliza varios lotes
  Cuando el sistema procesa las cantidades aportadas
  Entonces calcula la composición resultante mediante promedio ponderado por kg
  Y considera la operación como una mezcla.
```

### US18 — Finalizar transformación textil

Como operario, deseo finalizar una transformación para registrar sus lotes resultantes y el balance de cantidades.

```gherkin
Escenario 1: Balance válido
  Dado que existe una transformación iniciada
  Y el operario registra uno o más lotes de salida y, cuando corresponda, la cantidad y motivo del desperdicio
  Cuando solicita finalizarla
  Entonces el sistema calcula la merma como Entrada total − Salida total − Desperdicio
  Y finaliza cuando la merma es mayor o igual a cero
  Y establece las entradas como Procesado y las salidas como Disponible.
Escenario 2: Balance inválido
  Dado que la salida total más el desperdicio supera la entrada total
  Cuando intenta finalizar
  Entonces el sistema no permite finalizarla
  Y comunica que las cantidades no son válidas.
```

### US19 — Registrar desperdicio

Como operario, deseo registrar el desperdicio generado durante una transformación para conservar la cantidad perdida y su motivo.

```gherkin
Escenario 1: Transformación con desperdicio
  Dado que una transformación genera desperdicio
  Y el operario registra una cantidad mayor a cero
  Cuando completa la información
  Entonces el sistema solicita el motivo
  Y conserva cantidad y motivo como parte del balance.
```

### US20 — Marcar producto final

Como operario, deseo marcar un lote de salida como producto final para indicar que terminó su recorrido productivo y registrar sus datos comerciales básicos.

```gherkin
Escenario 1: Marcar producto final
  Dado que se genera un lote de salida
  Cuando el operario lo marca como producto final
  Entonces el sistema registra dicha condición
  Y mantiene el lote dentro de su genealogía.
Escenario 2: Registrar datos comerciales
  Dado que un lote se marca como producto final
  Cuando el operario registra distribuidor o comprador, precio, moneda, fecha de salida comercial o referencia comercial
  Entonces el sistema conserva los datos ingresados como información Privada.
Escenario 3: Precio con moneda
  Dado que se registra un precio de venta
  Cuando intenta guardar sin indicar moneda
  Entonces el sistema solicita completar la moneda.
Escenario 4: Operar producto final
  Dado que un lote está marcado como producto final
  Cuando se intenta dividirlo, transformarlo, transferirlo o incorporarlo nuevamente al recorrido productivo
  Entonces el sistema no permite la operación.
```

### US21 — Iniciar transferencia de lotes

Como operario, deseo iniciar una transferencia de lotes completos hacia otra empresa para continuar su recorrido dentro de la cadena.

```gherkin
Escenario 1: Inicio exitoso
  Dado que existen lotes habilitados
  Y selecciona una empresa destinataria de la misma cadena
  Cuando confirma la transferencia
  Entonces el sistema registra fecha y hora de inicio
  Y cambia los lotes a En transferencia
  Y alerta a los administradores receptores.
Escenario 2: Transferencia parcial
  Dado que se desea transferir solamente parte de un lote
  Cuando intenta iniciar la transferencia
  Entonces el sistema no lo permite
  Y requiere realizar previamente una división.
```

### US22 — Resolver transferencia recibida

Como administrador de empresa, deseo aceptar o rechazar una transferencia recibida para controlar la incorporación de lotes a mi empresa.

```gherkin
Escenario 1: Transferencia aceptada
  Dado que existe una transferencia Pendiente
  Cuando el administrador la acepta
  Entonces el sistema registra fecha y hora de recepción
  Y cambia la responsabilidad a la empresa receptora
  Y establece los lotes como Disponible
  Y conserva sus Traceability ID y QR.
Escenario 2: Transferencia rechazada
  Dado que existe una transferencia Pendiente
  Y registra el motivo del rechazo
  Cuando confirma el rechazo
  Entonces el sistema registra fecha y hora
  Y devuelve los lotes a la empresa de origen como Disponible
  Y alerta a sus administradores.
```

### US42 — Adjuntar evidencia fotográfica en producción

Como operario, deseo adjuntar fotografías a un lote o a una transformación registrada para dejar evidencia visual de su estado o del proceso realizado.

```gherkin
Escenario 1: Evidencia en lote
  Dado que existe un lote registrado
  Cuando el operario adjunta una o más fotografías
  Entonces el sistema las conserva asociadas al lote.
Escenario 2: Evidencia en transformación
  Dado que existe una transformación registrada
  Cuando el operario adjunta una o más fotografías
  Entonces el sistema las conserva asociadas a la transformación.
Escenario 3: Formato no soportado
  Dado que el archivo no corresponde a una imagen válida
  Cuando el operario intenta adjuntarlo
  Entonces el sistema lo rechaza y comunica los formatos permitidos.
```


## EP03 — Gestión de calidad y fallas

### US23 — Iniciar control de calidad

Como administrador de empresa, deseo iniciar un control de calidad sobre un lote para evaluar o reevaluar sus características según su situación.

```gherkin
Escenario 1: Evaluación inicial
  Dado que el lote tiene calidad No revisado
  Cuando el administrador inicia un control
  Entonces el sistema crea una Evaluación
  Y registra fecha y hora de inicio
  Y cambia la fase a En control de calidad.
Escenario 2: Reevaluación
  Dado que el lote tiene calidad Por reevaluar
  Cuando el administrador inicia un control
  Entonces el sistema crea una Reevaluación.
Escenario 3: Control activo existente
  Dado que el lote posee un control activo
  Cuando intenta iniciar otro
  Entonces el sistema no permite iniciarlo.
```

### US24 — Registrar pruebas de calidad

Como administrador de empresa, deseo registrar las pruebas realizadas durante un control para conservar los resultados obtenidos.

```gherkin
Escenario 1: Registro de prueba
  Dado que existe un control activo
  Y el administrador ingresa criterio, valor esperado, valor obtenido, unidad, resultado y observaciones
  Cuando confirma el registro
  Entonces el sistema conserva la prueba
  Y la asocia con el control correspondiente.
```

### US25 — Finalizar control de calidad

Como administrador de empresa, deseo finalizar un control de calidad para determinar el estado de calidad del lote.

```gherkin
Escenario 1: Todas las pruebas conformes
  Dado que todas las pruebas son Conformes
  Cuando finaliza el control
  Entonces registra fecha y hora de finalización
  Y devuelve la fase del lote a Disponible
  Y establece su calidad como Conforme.
Escenario 2: Control con falla
  Dado que una o más pruebas tienen resultado Con falla
  Cuando finaliza el control
  Entonces devuelve la fase del lote a Disponible
  Y establece su calidad como Con falla
  Y genera automáticamente una Falla
  Y mantiene el lote bloqueado.
```

### US26 — Gestionar falla

Como administrador de empresa, deseo completar y gestionar una falla para registrar su información y decidir el tratamiento del lote afectado.

```gherkin
Escenario 1: Completar información
  Dado que existe una Falla generada por un control
  Cuando registra causa, cantidad afectada y observaciones
  Entonces el sistema conserva la información
  Y mantiene su relación con el lote, control y pruebas fallidas.
Escenario 2: Enviar a reevaluación
  Dado que existe un lote Con falla
  Cuando decide reevaluarlo
  Entonces el sistema cambia su calidad a Por reevaluar
  Y lo mantiene bloqueado hasta completar la reevaluación.
```

### US27 — Descartar lote con falla

Como administrador de empresa, deseo descartar un lote con falla para retirarlo definitivamente de las operaciones productivas.

```gherkin
Escenario 1: Descarte de lote
  Dado que existe un lote Con falla
  Cuando el administrador decide descartarlo
  Entonces el sistema cambia su fase a Descartado
  Y registra la Falla como Confirmada.
```

### US28 — Resolver reevaluación

Como administrador de empresa, deseo finalizar la reevaluación de un lote para determinar si puede volver a operar o mantiene su falla.

```gherkin
Escenario 1: Reevaluación conforme
  Dado que todas las pruebas son Conformes
  Cuando finaliza el control
  Entonces el lote cambia a Conforme y queda desbloqueado
  Y la Falla se registra como Resuelta.
Escenario 2: Reevaluación con falla
  Dado que una o más pruebas vuelven a fallar
  Cuando finaliza el control
  Entonces el lote vuelve a Con falla
  Y queda pendiente de una nueva decisión administrativa.
```

### US43 — Adjuntar evidencia fotográfica en calidad

Como administrador de empresa, deseo adjuntar fotografías a un control de calidad o a una falla registrada para dejar evidencia visual de los resultados o del problema detectado.

```gherkin
Escenario 1: Evidencia en control de calidad
  Dado que existe un control de calidad registrado
  Cuando el administrador adjunta una o más fotografías
  Entonces el sistema las conserva asociadas al control.
Escenario 2: Evidencia en falla
  Dado que existe una Falla registrada
  Cuando el administrador adjunta una o más fotografías
  Entonces el sistema las conserva asociadas a la falla.
Escenario 3: Formato no soportado
  Dado que el archivo no corresponde a una imagen válida
  Cuando el operario intenta adjuntarlo
  Entonces el sistema lo rechaza y comunica los formatos permitidos.
```

### US44 — Generar reporte de control de calidad

Como administrador de empresa, deseo generar un reporte en PDF con los resultados de un control de calidad para compartirlo o conservarlo como evidencia documental.

```gherkin
Escenario 1: Generación exitosa
  Dado que existe un control de calidad finalizado
  Cuando el administrador solicita generar su reporte
  Entonces el sistema genera el documento con las pruebas, valores y resultados registrados
  Y no lo almacena dentro del sistema.
Escenario 2: Servicio no disponible
  Dado que el servicio de generación de PDF no responde
  Cuando el administrador intenta generar el reporte
  Entonces el sistema comunica el error
  Y permite reintentar.
```


## EP04 — Genealogía y trazabilidad

### US29 — Generar genealogía

Como usuario de TrazaTex, deseo que el sistema genere automáticamente la genealogía de los lotes para conservar sus relaciones dentro de la cadena textil.

```gherkin
Escenario 1: Relación por división
  Dado que un lote ha sido dividido
  Cuando se generan sus hijos
  Entonces el sistema registra la relación padre-hijo.
Escenario 2: Relación por transformación
  Dado que una transformación genera lotes de salida
  Cuando finaliza correctamente
  Entonces relaciona todas sus entradas con sus salidas.
Escenario 3: Transferencia
  Dado que un lote es transferido entre empresas
  Cuando se resuelve la transferencia
  Entonces el sistema conserva la transferencia dentro del historial del mismo lote
  Y no crea un nuevo lote ni una relación genealógica padre-hijo.
```

### US30 — Buscar genealogías

Como usuario autorizado, deseo buscar genealogías para localizar la trazabilidad que necesito consultar.

```gherkin
Escenario 1: Búsqueda de genealogía
  Dado que existen genealogías registradas
  Cuando el usuario realiza una búsqueda
  Entonces el sistema muestra las genealogías coincidentes
  Y permite acceder a aquellas que puede consultar.
```

### US31 — Consultar trazabilidad hacia atrás

Como usuario autorizado, deseo consultar los antecedentes de un lote para conocer su origen dentro de la cadena textil.

```gherkin
Escenario 1: Consulta hacia atrás
  Dado que existe un lote con antecedentes registrados
  Cuando consulta su trazabilidad hacia atrás
  Entonces el sistema muestra los lotes y relaciones que dieron origen al lote
  Y respeta las reglas de privacidad de las empresas involucradas.
```

### US32 — Consultar trazabilidad hacia adelante

Como usuario autorizado, deseo consultar los descendientes de un lote para conocer los recorridos generados a partir de él.

```gherkin
Escenario 1: Consulta hacia adelante
  Dado que existe un lote con descendientes registrados
  Cuando consulta su trazabilidad hacia adelante
  Entonces el sistema muestra los lotes derivados y sus relaciones
  Y representa las ramificaciones existentes.
```

### US33 — Detectar posible falla derivada

Como administrador de empresa, deseo que el sistema identifique lotes relacionados con una falla confirmada para conocer cuáles requieren revisión preventiva.

```gherkin
Escenario 1: Análisis de familia relacionada
  Dado que una Falla ha sido Confirmada
  Cuando el sistema analiza la genealogía hacia atrás
  Entonces utiliza los lotes históricos para localizar bifurcaciones y otros lotes relacionados de la misma familia.
Escenario 2: Descendientes relacionados
  Dado que existen divisiones dentro de las relaciones encontradas
  Cuando analiza sus descendientes
  Entonces revisa los lotes relacionados que continúan Disponibles.
Escenario 3: Posible falla derivada
  Dado que un lote relacionado continúa Disponible
  Cuando el sistema determina su posible relación con la falla
  Entonces cambia su calidad a Posible falla derivada
  Y lo bloquea preventivamente.
Escenario 4: Lote histórico
  Dado que un lote relacionado se encuentra Procesado, Dividido o Descartado
  Cuando analiza la genealogía
  Entonces lo utiliza únicamente para reconstruir las relaciones
  Y no lo devuelve a operación.
```

### US34 — Revisar posible falla derivada

Como administrador de empresa, deseo revisar un lote con posible falla derivada para confirmar o descartar que se encuentre afectado.

```gherkin
Escenario 1: Sin control previo
  Dado que el lote tiene calidad Posible falla derivada y nunca fue controlado
  Cuando inicia su revisión
  Entonces el sistema permite una Evaluación
  Y mantiene el lote bloqueado.
Escenario 2: Con control previo
  Dado que el lote posee controles anteriores
  Cuando inicia la revisión
  Entonces el sistema permite una Reevaluación
  Y mantiene el lote bloqueado.
Escenario 3: Resultado conforme
  Dado que todas las pruebas resultan Conformes
  Cuando finaliza el control
  Entonces el lote cambia a Conforme
  Y queda desbloqueado.
Escenario 4: Resultado con falla
  Dado que una o más pruebas presentan falla
  Cuando finaliza el control
  Entonces el lote cambia a Con falla
  Y genera su propia Falla.
```

### US35 — Recibir alerta por posible falla derivada

Como administrador de empresa, deseo recibir una alerta cuando un lote de mi empresa sea identificado como posible falla derivada para revisarlo oportunamente.

```gherkin
Escenario 1: Generación de alerta
  Dado que un lote cambia a Posible falla derivada
  Cuando el sistema determina su posible relación con una Falla Confirmada
  Entonces genera una alerta para los administradores responsables
  Y permite identificar el lote que requiere revisión.
```

### US36 — Consultar genealogía compartida

Como usuario autorizado, deseo visualizar una genealogía entre empresas para conocer el recorrido de los lotes respetando la privacidad B2B.

```gherkin
Escenario 1: Información autorizada
  Dado que el usuario consulta una genealogía con información de varias empresas
  Cuando visualiza sus lotes y relaciones
  Entonces el sistema muestra los campos Siempre Compartidos
  Y los campos Configurables establecidos como Compartidos por cada empresa.
Escenario 2: Información privada
  Dado que existen campos Configurables Privados o información Siempre Privada
  Cuando un usuario de otra empresa visualiza la genealogía
  Entonces el sistema mantiene dichos datos ocultos.
```


## EP05 — Análisis e indicadores

### US37 — Consultar indicadores de calidad

Como administrador de empresa, deseo consultar indicadores de calidad para conocer la situación de los lotes de mi empresa.

```gherkin
Escenario 1: Consulta por periodo
  Dado que existen registros dentro del periodo seleccionado
  Cuando el administrador consulta los indicadores
  Entonces el sistema muestra cantidades y porcentajes de lotes por estado de calidad
  Y presenta los resultados del periodo seleccionado.
```

### US38 — Analizar fallas registradas

Como administrador de empresa, deseo analizar las fallas registradas para conocer su cantidad y causas más frecuentes.

```gherkin
Escenario 1: Análisis de fallas
  Dado que existen fallas dentro del periodo seleccionado
  Cuando el administrador consulta el análisis
  Entonces el sistema muestra la cantidad de fallas y sus causas más frecuentes
  Y presenta los resultados del periodo.
```

### US39 — Analizar tiempos operativos

Como administrador de empresa, deseo consultar los tiempos promedio de las operaciones para analizar la duración de los procesos de mi empresa.

```gherkin
Escenario 1: Consulta de tiempos
  Dado que existen operaciones finalizadas dentro del periodo seleccionado
  Cuando el administrador consulta sus indicadores
  Entonces el sistema muestra la duración promedio de transformaciones, transferencias y controles de calidad
  Y presenta los resultados del periodo.
```

### US40 — Consultar pérdidas de material

Como administrador de empresa, deseo consultar la merma y desperdicio acumulados para analizar las pérdidas registradas en los procesos productivos.

```gherkin
Escenario 1: Consulta de pérdidas
  Dado que existen transformaciones finalizadas dentro del periodo seleccionado
  Cuando el administrador consulta los indicadores de material
  Entonces el sistema muestra la merma acumulada y el desperdicio acumulado
  Y presenta los resultados del periodo.
```
