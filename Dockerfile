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

RUN mkdir -p /app/uploads /app/data /opt/chemistry-bundle

COPY --from=build /workspace/build/libs/triples.war /app/app.war
# 케미 이미지는 호스트 uploads 볼륨에 없을 수 있으므로 이미지에 번들
COPY uploads/chemistry/ /opt/chemistry-bundle/
COPY scripts/docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh \
    && date -u +%Y%m%d%H%M%S > /opt/chemistry-bundle/.bundle-version

ENV SERVER_PORT=8080 \
    FILE_UPLOAD_DIR=/app/uploads

EXPOSE 8080

ENTRYPOINT ["/app/docker-entrypoint.sh"]
