#!/bin/bash
set -e

# Script to install Forge server with ATM10 from user-provided files
# User must manually download and place the ATM10 server files before building

echo "========================================"
echo "Installing Forge/ATM10 Server"
echo "========================================"

# Check if user provided the ATM10 server zip file
if [ ! -f "/tmp/forge-server/atm10-server.zip" ]; then
    cat <<EOF

================================================================================
ERROR: Forge/ATM10 server files not found
================================================================================

BUILD FAILED: You must provide the Forge/ATM10 server files before building.

CurseForge does not allow automated downloads. You need to manually download
the All the Mods 10 server files and place them in the build context.

Required steps:
  1. Download ATM10 server files from:
     https://www.curseforge.com/minecraft/modpacks/all-the-mods-10/files
  
  2. Look for "Server Files" download (not the client modpack)
  
  3. Create a directory: forge-server/
  
  4. Place the downloaded zip file as: forge-server/atm10-server.zip
  
  5. The directory structure should look like:
     project-root/
       ├── forge-server/
       │   └── atm10-server.zip
       ├── Dockerfile
       └── ... other files
  
  6. Rebuild with: docker compose build

For more information, see the README.md file.

================================================================================
EOF
    exit 1
fi

echo "✓ Found user-provided ATM10 server files"

# Verify it's actually a zip file
if ! file /tmp/forge-server/atm10-server.zip | grep -q "Zip archive"; then
    echo "ERROR: /tmp/forge-server/atm10-server.zip is not a valid zip file"
    exit 1
fi

echo "✓ Verified zip file integrity"

echo "Extracting ATM10 server files..."
unzip -q /tmp/forge-server/atm10-server.zip -d /mcserver-build/
echo "✓ ATM10 server files extracted successfully"

# Make sure we have necessary directories
mkdir -p /mcserver-build/mods /mcserver-build/config

# Create version marker file (using timestamp since version is user-provided)
date +%Y%m%d > /mcserver-build/.atm10_version

# Make shell scripts executable
find /mcserver-build -name "*.sh" -type f -exec chmod +x {} \;

echo "========================================"
echo "Forge/ATM10 installation completed"
echo "========================================"
