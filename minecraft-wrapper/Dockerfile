FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY build/libs/*.jar app.jar

USER 1000

EXPOSE 8092

ENTRYPOINT ["java", "-jar", "app.jar"]
