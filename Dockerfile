FROM ubuntu

# Install dependencies
RUN apt update
RUN DEBIAN_FRONTEND=noninteractive apt install -y wget git openjdk-21-jdk openjdk-21-jre

# Build server
WORKDIR /mcserver-build
RUN wget -O BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar
RUN git config --global --unset core.autocrlf || :
RUN java -jar BuildTools.jar --rev 1.21.4

# Copy resources and make post-create.sh executable
COPY ./resources /resources
RUN chmod +x /resources/post-create.sh

# Run server
WORKDIR /mcserver
EXPOSE 25565
ENTRYPOINT /resources/post-create.sh