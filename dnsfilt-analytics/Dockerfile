# Multi-stage Docker build for dnsfilt-analytics using Gradle 8 and Eclipse Temurin JDK 21
FROM gradle:8.10.2-jdk21-alpine AS builder
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle build --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy compiled application jar
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8081

# Memory flags optimized for 500MB container footprint (320MB Heap + 128MB Metaspace)
ENV JAVA_OPTS="-Xms128m -Xmx320m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
