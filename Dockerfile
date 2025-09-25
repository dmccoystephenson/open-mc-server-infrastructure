FROM ubuntu as base

# Install dependencies
RUN apt update
RUN DEBIAN_FRONTEND=noninteractive apt install -y wget git openjdk-21-jdk openjdk-21-jre

FROM base as builder

# Build server
WORKDIR /mcserver-build
RUN wget -O BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar
RUN git config --global --unset core.autocrlf || :
RUN java -jar BuildTools.jar --rev 1.21.8

FROM base as final

# Copy built server from builder stage
COPY --from=builder /mcserver-build/spigot-1.21.8.jar /mcserver-build/spigot-1.21.8.jar

# Copy resources and make post-create.sh executable
COPY ./resources /resources
RUN chmod +x /resources/post-create.sh

# Run server
WORKDIR /mcserver
EXPOSE 25565
ENTRYPOINT /resources/post-create.sh