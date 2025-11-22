#!/bin/bash
set -e

# Script to download and install Forge server with ATM10
# Arguments: ATM10_VERSION ATM10_FILE_ID1 ATM10_FILE_ID2

ATM10_VERSION="${1}"
ATM10_FILE_ID1="${2}"
ATM10_FILE_ID2="${3}"

echo "========================================"
echo "Installing All the Mods 10 (ATM10)"
echo "Version: ${ATM10_VERSION}"
echo "========================================"

# Build the download URLs
PRIMARY_URL="https://mediafilez.forgecdn.net/files/${ATM10_FILE_ID1}/${ATM10_FILE_ID2}/Server-Files-${ATM10_VERSION}.zip"
BACKUP_URL="https://edge.forgecdn.net/files/${ATM10_FILE_ID1}/${ATM10_FILE_ID2}/Server-Files-${ATM10_VERSION}.zip"

echo "Attempting to download ATM10 server files..."
echo "Primary URL: ${PRIMARY_URL}"

# Try to download from primary source with timeout
if wget --timeout=30 --tries=2 -O atm10-server.zip "${PRIMARY_URL}" 2>&1 | tee /tmp/wget.log; then
    echo "✓ Download successful from primary source"
else
    echo "✗ Primary source failed, trying backup URL..."
    echo "Backup URL: ${BACKUP_URL}"
    
    if wget --timeout=30 --tries=2 -O atm10-server.zip "${BACKUP_URL}" 2>&1 | tee /tmp/wget.log; then
        echo "✓ Download successful from backup source"
    else
        cat <<EOF

================================================================================
ERROR: Could not download ATM10 server files from any source
================================================================================

BUILD FAILED: ATM10 server files are required for SERVER_TYPE=forge.

Download attempted:
  - ATM10 Version: ${ATM10_VERSION}
  - File IDs: ${ATM10_FILE_ID1}/${ATM10_FILE_ID2}
  - Primary URL: ${PRIMARY_URL}
  - Backup URL: ${BACKUP_URL}

Possible causes:
  1. Network connectivity issues (check your internet connection)
  2. CurseForge CDN is temporarily unavailable
  3. The file IDs or version number are incorrect
  4. DNS resolution failure for forgecdn.net domains

Troubleshooting steps:
  1. Verify the ATM10 version and file IDs are correct by visiting:
     https://www.curseforge.com/minecraft/modpacks/all-the-mods-10/files
  
  2. To use a different ATM10 version, rebuild with:
     docker compose build --build-arg ATM10_VERSION=<version> \\
       --build-arg ATM10_FILE_ID1=<id1> --build-arg ATM10_FILE_ID2=<id2> mcserver

  3. Check the wget log for details:
EOF
        if [ -f /tmp/wget.log ]; then
            cat /tmp/wget.log
        fi
        
        exit 1
    fi
fi

# Verify the download succeeded
if [ ! -f atm10-server.zip ]; then
    echo "ERROR: ATM10 server zip file not found after download."
    exit 1
fi

echo "Extracting ATM10 server files..."
unzip -q atm10-server.zip -d /mcserver-build/
rm -f atm10-server.zip
echo "ATM10 server files extracted successfully"

# Make sure we have necessary directories
mkdir -p /mcserver-build/mods /mcserver-build/config

# Create version marker file for tracking ATM10 updates
echo "${ATM10_VERSION}" > /mcserver-build/.atm10_version

# Make shell scripts executable
find /mcserver-build -name "*.sh" -type f -exec chmod +x {} \;

echo "Forge server setup completed"
