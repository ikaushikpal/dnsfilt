# Multi-stage Docker build for dnsfilt-analytics
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app

# Copy Gradle wrapper first (enables layer caching)
COPY gradlew ./
COPY gradle ./gradle
RUN chmod +x gradlew

# Copy build config and source
COPY build.gradle settings.gradle ./
COPY src ./src

# Build application jar using Gradle wrapper
RUN ./gradlew build --no-daemon -x test

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8081

# Memory flags optimized for 500MB container footprint (320MB Heap + 128MB Metaspace)
ENV JAVA_OPTS="-Xms128m -Xmx320m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
