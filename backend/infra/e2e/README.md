# Cierre e2e en un stack aislado

`infra/e2e/run.sh` levanta un **stack propio y desechable** (proyecto `trazatex-e2e-final-api`: Kafka, PostgreSQL y Neo4j en los puertos 39092, 35432 y 37687) y corre sobre él
Production (38082), Traceability (38084) y Quality (38083) **reales**, con el flujo de cierre de la fase 06c: control con falla → descarte →
`FailureConfirmed` → Traceability → `PossibleDerivedFailureDetected` → `HOLD` real, incluida una transferencia en curso y la revisión de US34.

**No toca tu `docker-compose.yml` ni sus datos.** Las contraseñas se generan en cada corrida (no hay secretos en el repo) y al terminar el stack se destruye con `down -v`.

```bash
./mvnw -pl production,traceability,quality -am package -DskipTests   # construye los jars
infra/e2e/run.sh              # corre todo y destruye el stack (código de salida ≠ 0 si algo falla)
infra/e2e/run.sh guard-test   # comprueba, sin Docker, que la guarda rechaza borrar volúmenes de otro proyecto
infra/e2e/run.sh down         # destruye el stack si una corrida quedó a medias
```

Reglas: el nombre del proyecto es fijo; `safe_down` es el único camino que borra volúmenes y se niega si el proyecto no es `trazatex-e2e-final-api`, si
`COMPOSE_PROJECT_NAME` apunta a otro, o si algún contenedor a borrar pertenece a otro proyecto. Se niega a empezar si alguno de sus puertos está ocupado.
Las empresas se registran con eventos `CompanyRegistered` por Kafka (sustituyen a organization-access) y la identidad se manda por cabeceras directo a
cada servicio (sustituye al Gateway). Los logs de los servicios quedan en un directorio temporal que el script imprime.

**Determinismo.** El caso «lote en transferencia cuando Quality procesa el aviso» no se deja a la suerte: se detiene Traceability antes del descarte, Quality tras publicar `FailureConfirmed`, se espera el `PossibleDerivedFailureDetected` en el tópico y solo entonces se crea la transferencia. Cada arranque de un servicio deja su propio log. Requiere bash 3.2 o superior (el de macOS).
