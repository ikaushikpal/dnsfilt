# Multi-stage Docker build for dnsfilt-admin-backend
# Note: The compiled Angular frontend bundle is embedded in 'src/main/resources/static/'
# and packaged directly into app.jar, enabling Spring Boot to serve both REST APIs and UI.
FROM eclipse-temurin:26-jdk-alpine AS builder
WORKDIR /app

# Install Gradle 8.12
RUN apk add --no-cache wget unzip && \
    wget -q https://services.gradle.org/distributions/gradle-8.12-bin.zip -O /tmp/gradle.zip && \
    unzip -q /tmp/gradle.zip -d /opt && \
    ln -s /opt/gradle-8.12/bin/gradle /usr/local/bin/gradle && \
    rm /tmp/gradle.zip

COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle build --no-daemon -x test

FROM eclipse-temurin:26-jre-alpine
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

# Memory flags optimized for 500MB container footprint (320MB Heap + 128MB Metaspace)
ENV JAVA_OPTS="-Xms128m -Xmx320m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
