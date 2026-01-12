FROM debian:stable-slim AS builder
WORKDIR /src

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates curl xz-utils \
  && rm -rf /var/lib/apt/lists/*

ENV PATH="/opt/java/bin:${PATH}"
RUN mkdir -p /opt/java \
  && curl -fsSL https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.9%2B10/OpenJDK21U-jdk_x64_linux_hotspot_21.0.9_10.tar.gz \
  | tar -xz -C /opt/java --strip-components=1 \
  && java -version

# Copy Hytale server lib
COPY libs/ ./libs/

# Necessary code
COPY src/main/java ./src/main/java
COPY src/main/resources ./src/main/resources
COPY build.gradle ./build.gradle
COPY settings.gradle ./settings.gradle
COPY gradlew ./gradlew
COPY gradle/ ./gradle/

# Run gradle build
RUN ./gradlew build

# Expose build output
FROM debian:stable-slim AS artifact
WORKDIR /app
COPY --from=builder /src/build/libs/ ./build/libs/
