#!/bin/bash
# Test script to verify SERVER_TYPE configuration
set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to log test output
test_log() {
    echo -e "${YELLOW}[TEST]${NC} $1"
}

test_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

test_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Test counter
TESTS_RUN=0
TESTS_PASSED=0

test_log "🚀 Starting server type configuration test..."

# Test 1: Verify sample.env has SERVER_TYPE variable
test_log "Test 1: Checking if sample.env contains SERVER_TYPE..."
TESTS_RUN=$((TESTS_RUN + 1))
if grep -q "SERVER_TYPE=" sample.env; then
    test_success "✓ SERVER_TYPE found in sample.env"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    test_error "✗ SERVER_TYPE not found in sample.env"
fi

# Test 2: Verify default value is spigot
test_log "Test 2: Checking default SERVER_TYPE value..."
TESTS_RUN=$((TESTS_RUN + 1))
if grep -q "SERVER_TYPE=spigot" sample.env; then
    test_success "✓ Default SERVER_TYPE is set to spigot"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    test_error "✗ Default SERVER_TYPE is not spigot"
fi

# Test 3: Verify compose.yml uses SERVER_TYPE
test_log "Test 3: Checking if compose.yml uses SERVER_TYPE..."
TESTS_RUN=$((TESTS_RUN + 1))
if grep -q "SERVER_TYPE" compose.yml; then
    test_success "✓ compose.yml references SERVER_TYPE"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    test_error "✗ compose.yml does not reference SERVER_TYPE"
fi

# Test 4: Verify Dockerfile supports SERVER_TYPE
test_log "Test 4: Checking if Dockerfile supports SERVER_TYPE..."
TESTS_RUN=$((TESTS_RUN + 1))
if grep -q "ARG SERVER_TYPE" Dockerfile; then
    test_success "✓ Dockerfile has SERVER_TYPE argument"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    test_error "✗ Dockerfile missing SERVER_TYPE argument"
fi

# Test 5: Verify Dockerfile has Spigot build logic
test_log "Test 5: Checking Spigot build logic in Dockerfile..."
TESTS_RUN=$((TESTS_RUN + 1))
if grep -q "Build Spigot server" Dockerfile && grep -q "BuildTools.jar" Dockerfile; then
    test_success "✓ Dockerfile has Spigot build logic"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    test_error "✗ Dockerfile missing Spigot build logic"
fi

# Test 6: Verify Dockerfile has Mohist build logic
test_log "Test 6: Checking Mohist build logic in Dockerfile..."
TESTS_RUN=$((TESTS_RUN + 1))
if grep -q "Build Mohist server" Dockerfile && grep -q "mohistmc.com" Dockerfile; then
    test_success "✓ Dockerfile has Mohist build logic"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    test_error "✗ Dockerfile missing Mohist build logic"
fi

# Test 7: Verify post-create.sh handles SERVER_TYPE
test_log "Test 7: Checking if post-create.sh handles SERVER_TYPE..."
TESTS_RUN=$((TESTS_RUN + 1))
if grep -q "SERVER_TYPE" resources/post-create.sh; then
    test_success "✓ post-create.sh uses SERVER_TYPE"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    test_error "✗ post-create.sh does not use SERVER_TYPE"
fi

# Test 8: Verify post-create.sh creates mods directory for Mohist
test_log "Test 8: Checking if post-create.sh creates mods directory for Mohist..."
TESTS_RUN=$((TESTS_RUN + 1))
if grep -q "mkdir -p.*mods" resources/post-create.sh; then
    test_success "✓ post-create.sh creates mods directory"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    test_error "✗ post-create.sh does not create mods directory"
fi

# Test 9: Verify README documents SERVER_TYPE
test_log "Test 9: Checking if README documents SERVER_TYPE..."
TESTS_RUN=$((TESTS_RUN + 1))
if grep -q "SERVER_TYPE" README.md && grep -q "mohist" README.md; then
    test_success "✓ README documents SERVER_TYPE and Mohist"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    test_error "✗ README does not document SERVER_TYPE or Mohist"
fi

# Test 10: Verify Docker Compose config is valid with Spigot
test_log "Test 10: Validating Docker Compose config with SERVER_TYPE=spigot..."
TESTS_RUN=$((TESTS_RUN + 1))
cp sample.env .env.test
sed -i 's/YOUR_UUID_HERE/test-uuid-1234/g' .env.test
sed -i 's/YOUR_USERNAME_HERE/TestPlayer/g' .env.test
if docker compose --env-file .env.test config > /dev/null 2>&1; then
    test_success "✓ Docker Compose config valid with Spigot"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    test_error "✗ Docker Compose config invalid with Spigot"
fi

# Test 11: Verify Docker Compose config is valid with Mohist
test_log "Test 11: Validating Docker Compose config with SERVER_TYPE=mohist..."
TESTS_RUN=$((TESTS_RUN + 1))
sed -i 's/SERVER_TYPE=spigot/SERVER_TYPE=mohist/g' .env.test
if docker compose --env-file .env.test config > /dev/null 2>&1; then
    test_success "✓ Docker Compose config valid with Mohist"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    test_error "✗ Docker Compose config invalid with Mohist"
fi

# Cleanup
rm -f .env.test

# Final results
test_log "📊 Test Results:"
test_log "Tests passed: $TESTS_PASSED/$TESTS_RUN"

if [ "$TESTS_PASSED" -eq "$TESTS_RUN" ]; then
    test_success "🎉 All server type configuration tests passed!"
    exit 0
else
    test_error "❌ Some tests failed"
    exit 1
fi
