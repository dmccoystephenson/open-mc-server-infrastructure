#!/bin/bash
# Build script for the backup-manager

set -e

echo "Building backup-manager..."
cd "$(dirname "$0")/backup-manager"

# Build the Spring Boot application
./gradlew clean build -x test

echo "✓ Backup-manager built successfully"
echo "JAR file: backup-manager/build/libs/backup-manager-0.0.1.jar"
