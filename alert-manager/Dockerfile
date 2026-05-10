# Multi-stage build for alert-manager
# Stage 1: Build the application
FROM gradle:8.5-jdk21 AS builder

WORKDIR /build

# Copy build files for dependency resolution
COPY build.gradle .
COPY settings.gradle .

# Download dependencies (this layer will be cached unless build files change)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src src/

# Build the application (skip tests for faster builds)
RUN gradle clean build -x test --no-daemon

# Stage 2: Create the runtime image
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /build/build/libs/*.jar app.jar

RUN groupadd -r -g 1000 appgroup && useradd -r -u 1000 -g appgroup appuser && \
    chown appuser:appgroup app.jar

USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
