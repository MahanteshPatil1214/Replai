# --- build stage ---------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Warm the dependency cache so source changes don't re-download the world.
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# --- run stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/ticket-service-*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
