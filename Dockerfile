# Etapa 1: compilación
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

# Etapa 2: ejecución con usuario sin privilegios (uid 1000)
FROM eclipse-temurin:21-jre
RUN groupadd --gid 1000 app 2>/dev/null || true \
    && (id -u 1000 >/dev/null 2>&1 || useradd --uid 1000 --gid 1000 --no-create-home app) \
    && mkdir -p /app/efs /app/data && chown -R 1000:1000 /app
WORKDIR /app
COPY --from=build --chown=1000:1000 /build/target/eventos-service.jar app.jar
ENV EFS_BASE_PATH=/app/efs \
    DB_URL=jdbc:h2:file:/app/data/eventos \
    TZ=America/Santiago
USER 1000
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
