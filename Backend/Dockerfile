# ---- build stage: compile the Spring Boot jar with the Gradle wrapper ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy wrapper + build files first so dependency downloads are cached in a layer
COPY gradlew ./
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN ./gradlew --no-daemon dependencies --quiet || true

COPY src src
RUN ./gradlew --no-daemon bootJar

# ---- runtime stage: slim JRE image ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
