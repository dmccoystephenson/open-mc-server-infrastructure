#!/bin/bash

# Local CI validation script
# This script runs the same checks that the CI pipeline will run

set -e

echo "🚀 Running local CI validation..."

echo "📝 Checking shell scripts..."
bash -n up.sh
bash -n down.sh
bash -n resources/post-create.sh
echo "✅ Shell script syntax validation passed"

echo "🐳 Checking Docker configuration..."
docker compose config > /dev/null
echo "✅ Docker Compose validation passed"

echo "⚙️ Checking environment configuration..."
test -f sample.env
grep -q "MINECRAFT_VERSION=" sample.env
grep -q "OPERATOR_UUID=" sample.env
grep -q "OPERATOR_NAME=" sample.env
echo "✅ Environment configuration validation passed"

echo "📚 Checking documentation..."
test -f README.md
grep -q "# Private Minecraft Server" README.md
test -f LICENSE
echo "✅ Documentation validation passed"

echo "🔐 Checking file permissions..."
test -x up.sh
test -x down.sh
test -x resources/post-create.sh
echo "✅ File permissions validation passed"

echo "🎉 All local CI checks passed!"