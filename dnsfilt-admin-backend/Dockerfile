# Multi-stage Docker build for dnsfilt-admin-backend using Gradle 8
# Note: The compiled Angular frontend bundle is embedded in 'src/main/resources/static/'
# and packaged directly into app.jar, enabling Spring Boot to serve both REST APIs and UI.
FROM gradle:8.10.2-jdk21-alpine AS builder
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle build --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

# Memory flags optimized for 500MB container footprint (320MB Heap + 128MB Metaspace)
ENV JAVA_OPTS="-Xms128m -Xmx320m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
