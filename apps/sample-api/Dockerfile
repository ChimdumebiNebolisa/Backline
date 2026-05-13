# syntax=docker/dockerfile:1

FROM gradle:8.10-jdk21 AS builder
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew \
    && ./gradlew :apps:sample-api:bootJar --no-daemon -x test \
    && cp "$(ls apps/sample-api/build/libs/*.jar | grep -v plain | head -n 1)" /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN groupadd --system spring \
    && useradd --system spring --gid spring
USER spring:spring
WORKDIR /app
COPY --from=builder /workspace/app.jar /app/app.jar
EXPOSE 8081
ENV BACKLINE_SAMPLE_PORT=8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
