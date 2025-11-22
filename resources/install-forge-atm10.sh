#!/bin/bash
set -e

# Script to download and install Forge server with ATM10
# Arguments: ATM10_VERSION ATM10_FILE_ID1 ATM10_FILE_ID2

ATM10_VERSION="${1}"
ATM10_FILE_ID1="${2}"
ATM10_FILE_ID2="${3}"

echo "Downloading All the Mods 10 server files (version ${ATM10_VERSION})..."

# Try to download from primary and backup sources
if ! wget -O atm10-server.zip "https://mediafilez.forgecdn.net/files/${ATM10_FILE_ID1}/${ATM10_FILE_ID2}/Server-Files-${ATM10_VERSION}.zip" 2>/dev/null && \
   ! wget -O atm10-server.zip "https://edge.forgecdn.net/files/${ATM10_FILE_ID1}/${ATM10_FILE_ID2}/Server-Files-${ATM10_VERSION}.zip" 2>/dev/null; then
    cat <<EOF
ERROR: Could not download ATM10 server files from any source.
BUILD FAILED: ATM10 server files are required for SERVER_TYPE=forge.
Attempted to download: ATM10 version ${ATM10_VERSION} (file IDs: ${ATM10_FILE_ID1}/${ATM10_FILE_ID2})
The server cannot be built without ATM10 mods and will NOT be compatible with ATM10 clients.
Please check your network connection or verify the ATM10 version and file IDs.
To update ATM10 version, use: --build-arg ATM10_VERSION=<version> --build-arg ATM10_FILE_ID1=<id1> --build-arg ATM10_FILE_ID2=<id2>
EOF
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
