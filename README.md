# TrazaTex Backend

Backend de TrazaTex, una aplicación web orientada a la trazabilidad de lotes dentro de una cadena de producción textil.

El proyecto está desarrollado con Java y Spring Boot siguiendo una arquitectura de monolito modular. La aplicación se divide en los módulos Organization & Access, Production, Quality, Traceability, Notification y Analytics.

## Tecnologías

- Java 21
- Spring Boot
- Spring Security
- PostgreSQL
- Neo4j
- Apache Kafka
- Docker
- Maven
- OpenAPI / Swagger

## Ejecución local

Primero se deben levantar los servicios necesarios:

```bash
docker compose up -d