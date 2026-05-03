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

FROM eclipse-temurin:25.0.3_9-jdk-noble as java25-base

# Install tools needed for Spigot build (wget, git) and general use (curl)
RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        wget \
        git \
        curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

FROM eclipse-temurin:25.0.3_9-jdk-noble as java25-runtime

# Install runtime tools only — no build-time tools (wget/git) in the final image
RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

FROM java25-base as builder

# Accept Minecraft version as build argument
ARG MINECRAFT_VERSION=26.1

# Build server
WORKDIR /mcserver-build
RUN wget -O BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar
RUN git config --global --unset core.autocrlf || :
RUN java -jar BuildTools.jar --rev ${MINECRAFT_VERSION} && \
    if [ ! -f "spigot-${MINECRAFT_VERSION}.jar" ]; then \
        actual_jar=$(find . -maxdepth 1 -type f -newer BuildTools.jar -name "spigot-*.jar" | head -1); \
        if [ -z "$actual_jar" ]; then \
            echo "ERROR: BuildTools did not produce any spigot-*.jar in $(pwd)" >&2; \
            exit 1; \
        fi; \
        cp "$actual_jar" "spigot-${MINECRAFT_VERSION}.jar"; \
    fi

# Build minecraft-wrapper Spring Boot application
FROM base as wrapper-builder
WORKDIR /wrapper-build
COPY minecraft-wrapper/ .
# Build with tests - ensures code quality before creating Docker image
RUN ./gradlew build --no-daemon

FROM java25-runtime as final

# Accept Minecraft version as build argument
ARG MINECRAFT_VERSION=26.1

# Copy built server from builder stage
COPY --from=builder /mcserver-build/spigot-${MINECRAFT_VERSION}.jar /mcserver-build/spigot-${MINECRAFT_VERSION}.jar

# Copy minecraft-wrapper Spring Boot application
# Spring Boot Gradle plugin creates a JAR with the name pattern: {projectName}-{version}.jar
# We copy all JARs to ensure we get the executable JAR (not the -plain.jar)
COPY --from=wrapper-builder /wrapper-build/build/libs/minecraft-wrapper-*.jar /app/minecraft-wrapper.jar

# Copy resources and make scripts executable
COPY ./resources /resources
RUN chmod +x /resources/post-create.sh

# Run server
WORKDIR /mcserver
EXPOSE 25565
ENTRYPOINT exec /resources/post-create.sh
