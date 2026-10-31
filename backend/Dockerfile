# Build context: backend/ klasörü
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src ./src
RUN ./gradlew --no-daemon installDist -x test

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 app && mkdir -p /data/uploads && chown -R app /data
WORKDIR /app
COPY --from=build /src/build/install/soruhavuzu-backend ./
USER app
ENV PORT=8080 UPLOAD_DIR=/data/uploads
EXPOSE 8080
CMD ["./bin/soruhavuzu-backend"]
