FROM ubuntu as base

# Install dependencies
# Update package index and install without recommended packages to minimize dependencies
RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        wget \
        git \
        openjdk-21-jdk \
        curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

FROM base as builder

# Accept Minecraft version as build argument
ARG MINECRAFT_VERSION=1.21.10

# Build server
WORKDIR /mcserver-build
RUN wget -O BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar
RUN git config --global --unset core.autocrlf || :
RUN java -jar BuildTools.jar --rev ${MINECRAFT_VERSION}

# Build minecraft-wrapper Spring Boot application
FROM base as wrapper-builder
WORKDIR /wrapper-build
COPY minecraft-wrapper/ .
# Build with tests - ensures code quality before creating Docker image
RUN ./gradlew build --no-daemon

FROM base as final

# Accept Minecraft version as build argument
ARG MINECRAFT_VERSION=1.21.10

# Copy built server from builder stage
COPY --from=builder /mcserver-build/spigot-${MINECRAFT_VERSION}.jar /mcserver-build/spigot-${MINECRAFT_VERSION}.jar

# Copy minecraft-wrapper Spring Boot application
COPY --from=wrapper-builder /wrapper-build/build/libs/*.jar /app/minecraft-wrapper.jar

# Copy resources and make scripts executable
COPY ./resources /resources
RUN chmod +x /resources/post-create.sh

# Run server
WORKDIR /mcserver
EXPOSE 25565
ENTRYPOINT exec /resources/post-create.sh
