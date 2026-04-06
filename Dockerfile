# syntax=docker/dockerfile:1
#
# Builds the Linux native CLI (chuck) and optionally the IDE bundle.
#
# Usage — extract artifacts to ./dist/linux/:
#   docker build --output dist/linux .
#
# Or build and run interactively:
#   docker build -t chuck-builder .
#   docker run --rm chuck-builder --version
#
# The default output stage produces just the chuck binary.
# To also produce the IDE bundle, build the "full" target:
#   docker build --target full --output dist/linux .

# ─── Stage 1: GraalVM + Maven build environment ───────────────────────────────
FROM ghcr.io/graalvm/native-image-community:25 AS builder

# Install Maven (GraalVM CE image is OL9 / microdnf-based)
RUN microdnf install -y maven && microdnf clean all

WORKDIR /build

# Cache Maven dependencies as a separate layer (invalidated only on pom.xml change)
COPY pom.xml .
RUN mvn dependency:go-offline -B -q 2>/dev/null; true

# Copy source and examples
COPY src/ src/
COPY examples/ examples/
COPY examples_dsl/ examples_dsl/

# Build native CLI
RUN mvn -Pnative package -DskipTests -B

# Build IDE bundle (Linux app-image); skip AOT training run (headless container)
RUN mvn -Pide-bundle package -DskipTests -B -DskipAot=true

# ─── Stage 2: minimal output — just the native binary ─────────────────────────
FROM scratch AS default
COPY --from=builder /build/target/chuck /chuck

# ─── Stage 3: full output — binary + IDE bundle ───────────────────────────────
FROM scratch AS full
COPY --from=builder /build/target/chuck /chuck
COPY --from=builder /build/target/chuck-ide-bundle/chuck-ide/ /chuck-ide/

# ─── Stage 4: runnable image (for quick smoke tests) ──────────────────────────
FROM ubuntu:24.04 AS runnable
COPY --from=builder /build/target/chuck /usr/local/bin/chuck
ENTRYPOINT ["chuck"]
