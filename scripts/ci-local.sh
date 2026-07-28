#!/bin/bash

# Local CI validation script
# This script runs the same checks that the CI pipeline will run

set -e

echo "🚀 Running local CI validation..."

echo "📝 Checking shell scripts..."
bash -n up.sh
bash -n down.sh
bash -n upgrade.sh
bash -n rollback.sh
bash -n trigger-backup.sh
bash -n resources/post-create.sh
echo "✅ Shell script syntax validation passed"

echo "🐳 Checking Docker configuration..."
# Validate against a throwaway env file rather than the repo's .env — that file
# is gitignored and holds real credentials (RCON/admin passwords, Discord
# webhook, Anthropic API key), so it must never be overwritten or deleted here.
CI_ENV_FILE="$(mktemp)"
trap 'rm -f "${CI_ENV_FILE}"' EXIT
sed -e 's/YOUR_UUID_HERE/test-uuid/g' \
    -e 's/YOUR_USERNAME_HERE/testuser/g' \
    sample.env > "${CI_ENV_FILE}"
docker compose --env-file "${CI_ENV_FILE}" config > /dev/null
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
test -x rollback.sh
test -x trigger-backup.sh
test -x resources/post-create.sh
echo "✅ File permissions validation passed"

# Every Gradle module the CI pipeline tests. Keep this list in sync with the
# "Run <module> tests" steps in .github/workflows/ci.yml.
for module in web-app minecraft-wrapper agent-manager alert-manager backup-manager; do
    echo "🧪 Running ${module} tests..."
    (cd "${module}" && ./gradlew test --no-daemon)
    echo "✅ ${module} tests passed"
done

echo "🎉 All local CI checks passed!"