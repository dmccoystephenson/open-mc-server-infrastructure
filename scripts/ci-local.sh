#!/bin/bash

# Local CI validation script
# This script runs the same checks that the CI pipeline will run

set -e

echo "🚀 Running local CI validation..."

echo "📝 Checking shell scripts..."
bash -n up.sh
bash -n down.sh
bash -n upgrade.sh
bash -n monitor.sh
bash -n resources/post-create.sh
bash -n resources/minecraft-wrapper.sh
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
grep -q "# Private Minecraft Server" README.md
test -f LICENSE
echo "✅ Documentation validation passed"

echo "🔐 Checking file permissions..."
test -x up.sh
test -x down.sh
test -x upgrade.sh
test -x monitor.sh
test -x resources/post-create.sh
test -x resources/minecraft-wrapper.sh
echo "✅ File permissions validation passed"

echo "🧪 Testing graceful shutdown functionality..."
./scripts/test-graceful-shutdown.sh
echo "✅ Graceful shutdown test passed"

echo "📊 Testing monitoring script..."
# Test help output
./monitor.sh --help > /dev/null
# Test analysis with sample data
cat > /tmp/test-monitor.log << 'EOF'
Timestamp,CPU%,MemUsage,MemLimit,Mem%,NetIn,NetOut,BlockIn,BlockOut,PIDs
2025-10-12 10:00:00,45.5,1.2GiB,4GiB,30.0,10MB,5MB,50MB,20MB,25
2025-10-12 10:00:05,52.3,1.4GiB,4GiB,35.0,12MB,6MB,55MB,22MB,26
EOF
./monitor.sh -a /tmp/test-monitor.log > /dev/null
rm -f /tmp/test-monitor.log
echo "✅ Monitoring script test passed"

echo "🎉 All local CI checks passed!"