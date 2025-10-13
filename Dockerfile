FROM ubuntu as base

# Install dependencies
RUN apt update
RUN DEBIAN_FRONTEND=noninteractive apt install -y wget git openjdk-21-jdk openjdk-21-jre mailutils

FROM base as builder

# Accept Minecraft version as build argument
ARG MINECRAFT_VERSION=1.21.10

# Build server
WORKDIR /mcserver-build
RUN wget -O BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar
RUN git config --global --unset core.autocrlf || :
RUN java -jar BuildTools.jar --rev ${MINECRAFT_VERSION}

FROM base as final

# Accept Minecraft version as build argument
ARG MINECRAFT_VERSION=1.21.10

# Copy built server from builder stage
COPY --from=builder /mcserver-build/spigot-${MINECRAFT_VERSION}.jar /mcserver-build/spigot-${MINECRAFT_VERSION}.jar

# Copy resources and make scripts executable
COPY ./resources /resources
RUN chmod +x /resources/post-create.sh /resources/minecraft-wrapper.sh /resources/monitor-overload.sh

# Run server
WORKDIR /mcserver
EXPOSE 25565
ENTRYPOINT /resources/post-create.sh
