# syntax=docker/dockerfile:1
#
# Builds the Linux native CLI (chuck) and optionally the IDE bundle.
#
# Usage — extract artifacts to ./dist/linux/:
#   docker build --output dist/linux .
#
# The default output stage produces just the chuck binary.
# To also produce the IDE bundle, build the "full" target:
#   docker build --target full --output dist/linux .

# ─── Stage 1: GraalVM + Maven build environment ───────────────────────────────
FROM ghcr.io/graalvm/native-image-community:25 AS builder

# Install Maven (GraalVM CE image is OL9 / microdnf-based)
RUN microdnf install -y maven && microdnf clean all

WORKDIR /build

# Copy the parent pom and all modules
COPY pom.xml .
COPY chuck-core/ chuck-core/
COPY chuck-cli/ chuck-cli/
COPY chuck-ide/ chuck-ide/

# Build and Install everything to local m2 (required for inter-module dependencies)
RUN mvn install -DskipTests -B

# Build native CLI from the chuck-cli module
RUN mvn -Pnative -pl chuck-cli package -DskipTests -B

# Build IDE bundle from the chuck-ide module
# Note: jpackage might require a full GUI environment or certain libs even for app-image.
# In a standard container, we skip AOT but still attempt the bundle.
RUN mvn -Pide-bundle -pl chuck-ide package -DskipTests -B -DskipAot=true

# ─── Stage 2: minimal output — just the native binary ─────────────────────────
FROM scratch AS default
COPY --from=builder /build/chuck-cli/target/chuck /chuck

# ─── Stage 3: full output — binary + IDE bundle ───────────────────────────────
FROM scratch AS full
COPY --from=builder /build/chuck-cli/target/chuck /chuck
COPY --from=builder /build/chuck-ide/target/chuck-ide-bundle/chuck-ide/ /chuck-ide/

# ─── Stage 4: runnable image (for quick smoke tests) ──────────────────────────
FROM ubuntu:24.04 AS runnable
COPY --from=builder /build/chuck-cli/target/chuck /usr/local/bin/chuck
ENTRYPOINT ["chuck"]
