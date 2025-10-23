FROM ubuntu as base

# Install dependencies
RUN apt update
RUN DEBIAN_FRONTEND=noninteractive apt install -y wget git openjdk-21-jdk openjdk-21-jre

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

# Build Mohist server
RUN if [ "$SERVER_TYPE" = "mohist" ]; then \
        wget -O mohist-${MINECRAFT_VERSION}.jar https://mohistmc.com/api/v2/projects/mohist/${MINECRAFT_VERSION}/builds/latest/download || \
        wget -O mohist-${MINECRAFT_VERSION}.jar $(curl -s https://mohistmc.com/api/v2/projects/mohist/${MINECRAFT_VERSION}/builds/latest | grep -o '"url":"[^"]*"' | sed 's/"url":"//;s/"//'); \
    fi

FROM base as final

# Accept Minecraft version and server type as build arguments
ARG MINECRAFT_VERSION=1.21.10
ARG SERVER_TYPE=spigot

# Copy built server from builder stage based on server type
COPY --from=builder /mcserver-build/${SERVER_TYPE}-${MINECRAFT_VERSION}.jar /mcserver-build/${SERVER_TYPE}-${MINECRAFT_VERSION}.jar

# Copy resources and make scripts executable
COPY ./resources /resources
RUN chmod +x /resources/post-create.sh /resources/minecraft-wrapper.sh

# Run server
WORKDIR /mcserver
EXPOSE 25565
ENTRYPOINT /resources/post-create.sh
