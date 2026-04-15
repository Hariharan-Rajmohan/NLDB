# ---- Build Stage ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Install dos2unix to fix line endings from Windows
RUN apt-get update && apt-get install -y dos2unix

# Copy Gradle files
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# Fix line endings and permissions
RUN dos2unix gradlew && chmod +x gradlew

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src ./src
# .env is handled via Render Env Vars, but we copy if present for local testing
COPY .env* ./

# Build the application
RUN ./gradlew bootJar --no-daemon

# ---- Runtime Stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the built jar
# This pattern ensures we copy the main executable jar regardless of version suffix
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
