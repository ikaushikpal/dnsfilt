# Multi-stage Docker build for dnsfilt-resolver
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app

# Copy Gradle wrapper first (enables layer caching)
COPY gradlew ./
COPY gradle ./gradle
RUN chmod +x gradlew

# Copy build config and source
COPY build.gradle settings.gradle ./
COPY src ./src

# Build fat application jar using Gradle wrapper
RUN ./gradlew build --no-daemon -x test

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Copy compiled fat application jar
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 2053/udp 2053/tcp

# Memory flags optimized for 200MB container footprint (160MB Heap + 40MB Metaspace)
ENV JAVA_OPTS="-Xms64m -Xmx160m -XX:MaxMetaspaceSize=40m -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
