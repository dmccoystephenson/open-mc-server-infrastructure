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
        echo "Downloading All the Mods 10 server files..." && \
        ATM10_VERSION="1.15" && \
        ATM10_FILE_ID1="5847" && \
        ATM10_FILE_ID2="596" && \
        wget -O atm10-server.zip "https://mediafilez.forgecdn.net/files/${ATM10_FILE_ID1}/${ATM10_FILE_ID2}/Server-Files-${ATM10_VERSION}.zip" || \
        wget -O atm10-server.zip "https://edge.forgecdn.net/files/${ATM10_FILE_ID1}/${ATM10_FILE_ID2}/Server-Files-${ATM10_VERSION}.zip" || \
        (echo "Warning: Could not download ATM10 server files from primary sources" && \
         echo "Setting up basic Forge server instead..." && \
         FORGE_VERSION="1.21.1-52.0.29" && \
         wget -O forge-installer.jar "https://maven.minecraftforge.net/net/minecraftforge/forge/${FORGE_VERSION}/forge-${FORGE_VERSION}-installer.jar" && \
         java -jar forge-installer.jar --installServer && \
         mkdir -p /mcserver-build/mods); \
        \
        if [ -f atm10-server.zip ]; then \
            echo "Extracting ATM10 server files..." && \
            unzip -q atm10-server.zip -d /mcserver-build/ && \
            rm -f atm10-server.zip && \
            echo "ATM10 server files extracted successfully"; \
        fi && \
        \
        # Make sure we have necessary directories
        mkdir -p /mcserver-build/mods /mcserver-build/config && \
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
