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

# Intentionally using the JDK image: Eclipse Temurin 25 does not publish a separate JRE
# image for this tag, and the full JDK is required to run Spigot 26.1 at runtime.
# openjdk-21-jre-headless is added because Spring Boot 3.2.0 has runtime compatibility issues
# with Java 25 (embedded Tomcat and Spring internals rely on APIs removed/restricted in Java 25).
RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        openjdk-21-jre-headless \
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
        jar_count=$(find . -maxdepth 1 -type f -newer BuildTools.jar -name "spigot-*.jar" | wc -l); \
        if [ "$jar_count" -eq 0 ]; then \
            echo "ERROR: BuildTools did not produce any spigot-*.jar in $(pwd)" >&2; \
            exit 1; \
        fi; \
        if [ "$jar_count" -gt 1 ]; then \
            echo "ERROR: BuildTools produced multiple spigot-*.jar candidates; cannot determine which to use" >&2; \
            exit 1; \
        fi; \
        actual_jar=$(find . -maxdepth 1 -type f -newer BuildTools.jar -name "spigot-*.jar"); \
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
RUN chmod +x /resources/post-create.sh && \
    groupadd -r -g 1000 appgroup && \
    useradd -r -u 1000 -g appgroup appuser

USER appuser

# Run server
WORKDIR /mcserver
EXPOSE 25565
ENTRYPOINT exec /resources/post-create.sh
