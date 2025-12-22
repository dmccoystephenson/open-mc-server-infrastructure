# Multi-stage build for upgrade-manager
# Stage 1: Build the application
FROM gradle:8.5-jdk21 AS builder

WORKDIR /build

# Copy build files for dependency resolution
COPY build.gradle .
COPY settings.gradle .
COPY gradle gradle/

# Download dependencies (this layer will be cached unless build files change)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src src/

# Build the application (skip tests for faster builds)
RUN gradle clean build -x test --no-daemon

# Stage 2: Create the runtime image
# Using Ubuntu-based image for reliable package installation
FROM eclipse-temurin:21-jre

# Install docker CLI and curl
RUN apt-get update && \
    apt-get install -y --no-install-recommends docker.io curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy the JAR file from builder stage
COPY --from=builder /build/build/libs/*.jar app.jar

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
