# TRACEABILITY
Genealogía textil y privacidad entre empresas.

Tecnologías: Java 21, Spring Boot, Neo4j y Kafka.
Hola, para abrirlo en local revisa [INSTRUCCIONES_DESPLIEGUE_LOCAL.md](../../INSTRUCCIONES_DESPLIEGUE_LOCAL.md) :)

## Ejecución local

- Puerto: `8084`
- Variables de entorno:
    - `NEO4J_URI`: conexión a Neo4j (obligatoria)
    - `KAFKA_BOOTSTRAP_SERVERS`: brokers de Kafka (por defecto `localhost:9092`)
- Perfil local: `application-local.yml`
