# syntax=docker/dockerfile:1

FROM gradle:8.10-jdk21 AS builder
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew \
    && ./gradlew :apps:api:bootJar --no-daemon -x test \
    && cp "$(ls apps/api/build/libs/*.jar | grep -v plain | head -n 1)" /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring \
    && useradd --system spring --gid spring
USER spring:spring
WORKDIR /app
COPY --from=builder /workspace/app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
