FROM ubuntu as base

# Install dependencies
# Update package index and install without recommended packages to minimize dependencies
RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        wget \
        git \
        openjdk-21-jdk \
        curl \
        unzip && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

FROM base as builder

# Accept Minecraft version and server type as build arguments
ARG MINECRAFT_VERSION=1.21.10
ARG SERVER_TYPE=spigot
# ATM10 version and file IDs (can be overridden for updates)
ARG ATM10_VERSION=1.15
ARG ATM10_FILE_ID1=5847
ARG ATM10_FILE_ID2=596

# Build server
WORKDIR /mcserver-build

# Build Spigot server
RUN if [ "$SERVER_TYPE" = "spigot" ]; then \
        wget -O BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar && \
        git config --global --unset core.autocrlf || : && \
        java -jar BuildTools.jar --rev ${MINECRAFT_VERSION}; \
    fi

# Download and install Forge server with ATM10
RUN if [ "$SERVER_TYPE" = "forge" ]; then \
        # Download ATM10 server files which includes Forge installer
        echo "Downloading All the Mods 10 server files (version ${ATM10_VERSION})..." && \
        (wget -O atm10-server.zip "https://mediafilez.forgecdn.net/files/${ATM10_FILE_ID1}/${ATM10_FILE_ID2}/Server-Files-${ATM10_VERSION}.zip" || \
         wget -O atm10-server.zip "https://edge.forgecdn.net/files/${ATM10_FILE_ID1}/${ATM10_FILE_ID2}/Server-Files-${ATM10_VERSION}.zip") || \
        (echo "ERROR: Could not download ATM10 server files from any source." && \
         echo "BUILD FAILED: ATM10 server files are required for SERVER_TYPE=forge." && \
         echo "The server cannot be built without ATM10 mods and will NOT be compatible with ATM10 clients." && \
         echo "Please check your network connection or verify the ATM10 version and file IDs." && \
         echo "To update ATM10 version, use: --build-arg ATM10_VERSION=<version> --build-arg ATM10_FILE_ID1=<id1> --build-arg ATM10_FILE_ID2=<id2>" && \
         exit 1); \
        \
        if [ -f atm10-server.zip ]; then \
            echo "Extracting ATM10 server files..." && \
            unzip -q atm10-server.zip -d /mcserver-build/ && \
            rm -f atm10-server.zip && \
            echo "ATM10 server files extracted successfully"; \
        else \
            echo "ERROR: ATM10 server zip file not found after download." && \
            exit 1; \
        fi && \
        \
        # Make sure we have necessary directories
        mkdir -p /mcserver-build/mods /mcserver-build/config && \
        \
        # Create version marker file for tracking ATM10 updates
        echo "${ATM10_VERSION}" > /mcserver-build/.atm10_version && \
        \
        # If there's a startserver.sh or similar, make it executable
        find /mcserver-build -name "*.sh" -type f -exec chmod +x {} \; && \
        \
        echo "Forge server setup completed"; \
    fi

FROM base as final

# Accept Minecraft version and server type as build arguments
ARG MINECRAFT_VERSION=1.21.10
ARG SERVER_TYPE=spigot

# Copy everything from builder - we'll handle what exists in the setup script
COPY --from=builder /mcserver-build /mcserver-build

# Copy resources and make scripts executable
COPY ./resources /resources
RUN chmod +x /resources/post-create.sh /resources/minecraft-wrapper.sh

# Run server
WORKDIR /mcserver
EXPOSE 25565
ENTRYPOINT exec /resources/post-create.sh
