#!/bin/bash
set -euo pipefail

# Build script for backup-manager

cd "$(dirname "$0")"

echo "Building backup-manager..."
./gradlew clean build --no-daemon

echo "Backup-manager built successfully!"
