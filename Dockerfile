FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

RUN chmod +x ./gradlew
RUN ./gradlew bootJar -x test -x asciidoctor --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

ARG SERVER_PORT=19700
EXPOSE ${SERVER_PORT}

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
