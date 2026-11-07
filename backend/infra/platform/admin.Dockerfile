FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
ENV MAVEN_OPTS="-Xmx512m"
COPY backend/infra/platform/admin-server/ .
RUN --mount=type=cache,id=trazatex-admin-maven,target=/root/.m2 mvn -B -ntp package -DskipTests
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/target/operations-admin-1.0.0.jar service.jar
ENTRYPOINT ["java","-jar","/app/service.jar"]
