# ---- Etapa 1: compilar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests

# ---- Etapa 2: ejecutar ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# mysqldump: necesario para los backups automaticos
RUN apt-get update && apt-get install -y --no-install-recommends default-mysql-client \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
