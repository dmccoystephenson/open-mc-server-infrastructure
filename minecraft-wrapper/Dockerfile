FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY build/libs/*.jar app.jar

RUN addgroup -S -g 1000 appgroup && adduser -S -u 1000 -G appgroup appuser && \
    chown appuser:appgroup app.jar

USER appuser

EXPOSE 8092

ENTRYPOINT ["java", "-jar", "app.jar"]
