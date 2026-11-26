# ---- Build stage: compile and test with Maven ----
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /app

# Download dependencies first so this layer is cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/tally-*.jar extract --layers --launcher --destination extracted

# ---- Runtime stage: small JRE image, non-root user ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S tally && adduser -S tally -G tally
USER tally

# Layered jar: dependencies change rarely, so they get their own cached layers
COPY --from=build /app/extracted/dependencies/ ./
COPY --from=build /app/extracted/spring-boot-loader/ ./
COPY --from=build /app/extracted/snapshot-dependencies/ ./
COPY --from=build /app/extracted/application/ ./

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
