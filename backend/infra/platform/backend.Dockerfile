FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
ARG MODULE
ENV MAVEN_OPTS="-Xmx512m"
COPY backend/ .
# -DskipTests a propósito: las pruebas de integración usan Testcontainers y necesitan Docker, que no existe dentro de la
# construcción de una imagen. La puerta de calidad va ANTES de construir las imágenes, en backend/:
#   ./mvnw -B -ntp verify    (unitarias, integración y ArchUnit de todos los módulos; debe terminar en BUILD SUCCESS)
RUN --mount=type=cache,id=trazatex-backend-maven,target=/root/.m2,sharing=locked mvn -B -ntp -pl ${MODULE} -am package -DskipTests
FROM eclipse-temurin:21-jre-alpine AS runtime
ARG MODULE
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/*-exec.jar /app/service.jar
ENTRYPOINT ["java","-jar","/app/service.jar"]
