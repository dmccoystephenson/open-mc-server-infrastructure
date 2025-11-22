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

# For Forge: Copy user-provided ATM10 server files and installation script
# User must manually download ATM10 server files and place them in forge-server/ directory
COPY ./resources/install-forge-atm10.sh /tmp/install-forge-atm10.sh
RUN chmod +x /tmp/install-forge-atm10.sh

# Copy user-provided Forge/ATM10 files (required for SERVER_TYPE=forge)
# For Spigot builds, create an empty directory so COPY doesn't fail
RUN mkdir -p /tmp/forge-server
COPY ./forge-server/ /tmp/forge-server/

# Install Forge server with ATM10 from user-provided files
RUN if [ "$SERVER_TYPE" = "forge" ]; then \
        /tmp/install-forge-atm10.sh; \
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
