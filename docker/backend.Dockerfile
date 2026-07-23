FROM eclipse-temurin:17-jdk AS build

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre

WORKDIR /app

RUN groupadd --system prizm \
    && useradd --system --gid prizm --create-home --home-dir /app prizm \
    && mkdir -p /app/var/storage /app/var/tmp \
    && chown -R prizm:prizm /app

COPY --from=build --chown=prizm:prizm /workspace/build/libs/*.jar /app/app.jar

USER prizm

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
