# ---- Build Stage ----
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copy Gradle wrapper and build files first for dependency caching
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Copy source (includes pre-generated jOOQ sources in src/generated/jooq/) and build.
# generateJooq requires a live ClickHouse at localhost:8123 which is unavailable
# during image build, so it is skipped here. Re-run ./gradlew generateJooq locally
# whenever the ClickHouse schema changes, then rebuild the image.
COPY src/ src/
RUN ./gradlew bootJar -x generateJooq --no-daemon

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8084

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8084/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
