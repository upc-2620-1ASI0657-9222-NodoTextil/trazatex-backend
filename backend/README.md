# BACKEND
Servicios de negocio y API Gateway de TrazaTex.

Tecnologías: Java 21, Spring Boot y Maven multimódulo.
Quality conserva la implementación recibida y genera PDF con Thymeleaf y OpenHTMLtoPDF. Notification procesa eventos con deduplicación y entrega diferida mediante SendGrid. Analytics mantiene proyecciones de lectura por empresa para los cuatro indicadores del informe.

Las migraciones de cada servicio se aplican al arrancar. Las pruebas están en `src/test`; los escenarios de aceptación se contrastan con [las historias del informe](docs/domain/user-stories.md). Consulta [el despliegue local](../INSTRUCCIONES_DESPLIEGUE_LOCAL.md).

**Puerta de calidad.** Antes de construir las imágenes, ejecuta en `backend/` `./mvnw -B -ntp verify` con Docker encendido (las pruebas de integración usan Testcontainers) y comprueba que termine en `BUILD SUCCESS`. `infra/platform/backend.Dockerfile` compila con `-DskipTests` porque dentro de la construcción de una imagen no hay Docker para Testcontainers: no sustituye esa verificación.
