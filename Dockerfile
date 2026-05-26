# syntax=docker/dockerfile:1.7

# ----- build stage -----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY src src
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

# ----- runtime stage -----
FROM eclipse-temurin:21-jre-alpine
ENV SPRING_PROFILES_ACTIVE=prod
WORKDIR /app
RUN addgroup -S konvo && adduser -S konvo -G konvo
COPY --from=build /workspace/build/libs/konvo-backend.jar /app/konvo-backend.jar
USER konvo
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/konvo-backend.jar"]
