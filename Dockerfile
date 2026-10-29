# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY client/build.gradle.kts client/
COPY openapi openapi
# 依存解決を先に行い、ソース変更時もこのレイヤーをキャッシュさせる
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies --no-daemon -q > /dev/null
COPY src src
COPY client/src client/src

# 生成コードが仕様と一致していることを確かめる。仕様だけ直して生成し忘れた変更や、生成コードを手で直した変更をここで落とす
FROM build AS check-generated
RUN --mount=type=cache,target=/root/.gradle \
    cp -r src/generated /tmp/server && cp -r client/src/generated /tmp/client \
    && ./gradlew generate --no-daemon \
    && diff -r /tmp/server src/generated && diff -r /tmp/client client/src/generated \
    || { echo '生成コードが仕様と一致しません。./gradlew generate（集約リポジトリでは ./scripts/generate.sh）を実行し、生成物もコミットしてください' >&2; exit 1; }

# docker build --target test で単体テストを実行する（ホスト側に JDK は不要）
FROM check-generated AS test
RUN --mount=type=cache,target=/root/.gradle ./gradlew test --no-daemon

FROM build AS package
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-noble AS runtime
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=package /workspace/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
