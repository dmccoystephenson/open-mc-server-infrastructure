#!/bin/bash

# Local CI validation script
# This script runs the same checks that the CI pipeline will run

set -e

echo "🚀 Running local CI validation..."

echo "📝 Checking shell scripts..."
bash -n up.sh
bash -n down.sh
bash -n upgrade.sh
bash -n trigger-backup.sh
bash -n resources/post-create.sh
echo "✅ Shell script syntax validation passed"

echo "🐳 Checking Docker configuration..."
# Create a temporary .env file for validation
cp sample.env .env
sed -i 's/YOUR_UUID_HERE/test-uuid/g' .env
sed -i 's/YOUR_USERNAME_HERE/testuser/g' .env
docker compose config > /dev/null
rm .env
echo "✅ Docker Compose validation passed"

echo "⚙️ Checking environment configuration..."
test -f sample.env
grep -q "MINECRAFT_VERSION=" sample.env
grep -q "OPERATOR_UUID=" sample.env
grep -q "OPERATOR_NAME=" sample.env
echo "✅ Environment configuration validation passed"

echo "📚 Checking documentation..."
test -f README.md
grep -q "# Open Minecraft Server Infrastructure" README.md
test -f LICENSE
echo "✅ Documentation validation passed"

echo "🔐 Checking file permissions..."
test -x up.sh
test -x down.sh
test -x upgrade.sh
test -x trigger-backup.sh
test -x resources/post-create.sh
echo "✅ File permissions validation passed"

echo "🧪 Running minecraft-wrapper tests..."
cd minecraft-wrapper
./gradlew test --no-daemon
cd ..
echo "✅ Minecraft wrapper tests passed"

echo "🎉 All local CI checks passed!"