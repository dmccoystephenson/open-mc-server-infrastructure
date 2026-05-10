# Multi-stage build for backup-manager
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
# Using Ubuntu-based image for reliable package installation
# Alpine had network issues with apk in the build environment
FROM eclipse-temurin:21-jre

# Install tar, gzip, and curl
RUN apt-get update && \
    apt-get install -y --no-install-recommends tar gzip curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /build/build/libs/*.jar app.jar

ENV HOME=/tmp
USER 1000

ENTRYPOINT ["java", "-jar", "app.jar"]
