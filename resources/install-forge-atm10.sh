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
# We check if wget succeeds AND if the file is actually a valid zip
DOWNLOAD_SUCCESS=false

if wget --timeout=30 --tries=2 -O atm10-server.zip "${PRIMARY_URL}" 2>&1 | tee /tmp/wget.log; then
    # Check if it's actually a zip file (not an error page)
    if file atm10-server.zip | grep -q "Zip archive"; then
        echo "✓ Download successful from primary source"
        DOWNLOAD_SUCCESS=true
    else
        echo "✗ Primary source returned invalid file (likely error page)"
        rm -f atm10-server.zip
    fi
fi

if [ "$DOWNLOAD_SUCCESS" = false ]; then
    echo "✗ Trying backup URL..."
    echo "Backup URL: ${BACKUP_URL}"
    
    if wget --timeout=30 --tries=2 -O atm10-server.zip "${BACKUP_URL}" 2>&1 | tee /tmp/wget.log; then
        if file atm10-server.zip | grep -q "Zip archive"; then
            echo "✓ Download successful from backup source"
            DOWNLOAD_SUCCESS=true
        else
            echo "✗ Backup source returned invalid file (likely error page)"
            rm -f atm10-server.zip
        fi
    fi
fi

if [ "$DOWNLOAD_SUCCESS" = false ]; then
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
  1. HTTP 403 Forbidden - CurseForge may be blocking automated downloads
  2. The file IDs or version number are incorrect
  3. CurseForge CDN requires authentication or has changed access policies
  4. The download URL structure has changed

IMPORTANT: CurseForge has restrictions on automated downloads. The 403 Forbidden
error suggests that direct wget downloads may not be allowed for this file.

Troubleshooting steps:
  1. Verify the ATM10 version and file IDs are correct by visiting:
     https://www.curseforge.com/minecraft/modpacks/all-the-mods-10/files
  
  2. Check if the file requires authentication or has download restrictions
  
  3. Alternative: Download the server files manually and place them in a
     location accessible to the build, then modify the script to use a local file
  
  4. To use a different ATM10 version, rebuild with:
     docker compose build --build-arg ATM10_VERSION=<version> \\
       --build-arg ATM10_FILE_ID1=<id1> --build-arg ATM10_FILE_ID2=<id2> mcserver

  5. Check the wget log for details:
EOF
    if [ -f /tmp/wget.log ]; then
        cat /tmp/wget.log
    fi
    
    exit 1
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
