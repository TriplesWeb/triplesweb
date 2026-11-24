# syntax=docker/dockerfile:1

FROM node:20-bookworm-slim AS css
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
# 배포/운영 스크립트가 바뀌어도 CSS 레이어 캐시가 무효화되지 않도록
# CSS 빌드에 필요한 파일만 포함한다.
COPY scripts/setup-css.sh scripts/build-*css.cjs scripts/build-tw4.cjs scripts/tailwind-cli.cjs ./scripts/
COPY scripts/css ./scripts/css
COPY src/main/resources ./src/main/resources
RUN chmod +x scripts/setup-css.sh \
    && ./scripts/setup-css.sh --no-run --skip-install

FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# Dependency layer — rebuilt only when Gradle files change
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew \
    && ./gradlew dependencies --no-daemon || true

COPY src/main ./src/main
COPY --from=css /workspace/src/main/resources/static/css/site.css ./src/main/resources/static/css/site.css
COPY --from=css /workspace/src/main/resources/static/css/triples.css ./src/main/resources/static/css/triples.css
COPY --from=css /workspace/src/main/resources/static/css/objekt.css ./src/main/resources/static/css/objekt.css
COPY --from=css /workspace/src/main/resources/static/css/gravity.css ./src/main/resources/static/css/gravity.css
# 검증은 PR CI에서 수행. 운영 이미지는 테스트 재실행 없이 패키징만 수행.
RUN ./gradlew bootWar -x test --no-daemon

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN mkdir -p /app/uploads /app/data

COPY --from=build /workspace/build/libs/triples.war /app/app.war
# 케미 정적 파일은 배포 중 호스트 uploads/chemistry에 별도 동기화합니다.
# 이미지에 수백 MB 정적 자산을 포함하지 않아 PR마다 재전송하지 않습니다.
ENV SERVER_PORT=8080 \
    FILE_UPLOAD_DIR=/app/uploads

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-XX:InitialRAMPercentage=5", "-XX:+ExitOnOutOfMemoryError", "-XX:+UseStringDeduplication", "-jar", "/app/app.war"]
