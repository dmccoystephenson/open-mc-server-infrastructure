# Multi-stage build for web-app
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

# Copy the JAR file from builder stage
COPY --from=builder /build/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
