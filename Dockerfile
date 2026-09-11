# ---- build ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# dependency layer cache
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline -DskipTests || true

COPY src src
RUN ./mvnw -B -DskipTests package \
 && mv target/konecta-stores-and-stock-service-*.jar /workspace/app.jar

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /workspace/app.jar /app/app.jar

ENV SERVER_PORT=8092 \
    TZ=Africa/Maputo \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8092

# Adjust path if your actuator base path differs
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8092/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
