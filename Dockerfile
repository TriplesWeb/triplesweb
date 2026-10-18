# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# Dependency layer — rebuilt only when Gradle files change
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew \
    && ./gradlew dependencies --no-daemon || true

COPY src ./src
RUN ./gradlew bootWar -x test --no-daemon

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN mkdir -p /app/uploads /app/data

COPY --from=build /workspace/build/libs/triples.war /app/app.war

ENV SERVER_PORT=8080 \
    FILE_UPLOAD_DIR=/app/uploads

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.war"]
