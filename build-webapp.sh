#!/bin/bash
# Build script for the web application

set -e

echo "Building web application..."
cd "$(dirname "$0")/web-app"

# Build the Spring Boot application
./gradlew clean build -x test

echo "✓ Web application built successfully"
echo "JAR file: web-app/build/libs/web-app-0.0.1.jar"
