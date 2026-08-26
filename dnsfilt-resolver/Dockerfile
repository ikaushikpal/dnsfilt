# Multi-stage Docker build for dnsfilt-resolver
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

# Copy compiled fat application jar
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 2053/udp 2053/tcp

# Memory flags optimized for 200MB container footprint (160MB Heap + 40MB Metaspace)
ENV JAVA_OPTS="-Xms64m -Xmx160m -XX:MaxMetaspaceSize=40m -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
