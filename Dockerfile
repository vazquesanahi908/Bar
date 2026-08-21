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
# -Duser.timezone fuerza la zona horaria de TODO el programa Java (no solo
# de las respuestas JSON, que ya la tenían bien puesta en
# application.properties). Sin esto, el contenedor arranca con la zona
# horaria por defecto del sistema operativo base (casi seguro UTC), y ahí es
# donde se generaba el desfasaje de 3 horas: la hora se guardaba en la base
# ya convertida a UTC en vez de guardarse tal cual se la mandó el usuario.
ENTRYPOINT ["java","-Duser.timezone=America/Argentina/Buenos_Aires","-jar","/app/app.jar"]
