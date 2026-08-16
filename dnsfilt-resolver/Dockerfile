# Multi-stage Docker build for dnsfilt-resolver using Gradle 8 and Eclipse Temurin JDK 21
FROM gradle:8.10.2-jdk21-alpine AS builder
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle build --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Ensure certs directory exists for runtime volume mounts (-v /path/to/certs:/app/certs)
RUN mkdir -p /app/certs

# Copy compiled fat application jar
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 2053/udp 2053/tcp

# Memory flags optimized for 200 MB container footprint (160m Heap + 40m Metaspace)
ENV JAVA_OPTS="-Xms64m -Xmx160m -XX:MaxMetaspaceSize=40m -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
