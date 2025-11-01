# Using Ubuntu-based image for reliable package installation
# Alpine had network issues with apk in the build environment
FROM eclipse-temurin:21-jre

# Install docker CLI
RUN apt-get update && apt-get install -y docker.io && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy the JAR file
COPY build/libs/*.jar app.jar

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
