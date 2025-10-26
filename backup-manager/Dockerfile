FROM eclipse-temurin:21-jre-alpine

# Install bash and docker CLI
RUN apk add --no-cache bash docker-cli

# Set working directory
WORKDIR /app

# Copy the JAR file
COPY build/libs/*.jar app.jar

# Expose port (not really used, but good practice)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
