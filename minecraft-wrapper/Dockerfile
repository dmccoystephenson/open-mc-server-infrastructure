FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built JAR file
COPY build/libs/*.jar app.jar

# Expose the port
EXPOSE 8092

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
