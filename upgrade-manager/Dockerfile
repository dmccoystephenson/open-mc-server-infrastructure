FROM gradle:8.5-jdk21 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN gradle build -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# Copy shell scripts that might be needed for upgrade operations
COPY --from=build /app /workdir

ENTRYPOINT ["java", "-jar", "app.jar"]
