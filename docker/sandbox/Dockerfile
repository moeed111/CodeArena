# LeetCode Sandbox Image
# This image is used to execute user-submitted Java code in isolation.
# It has NO network access and runs as a non-root user.
FROM eclipse-temurin:17-jdk-alpine

# Install only what's needed for Java compilation and execution
RUN apk add --no-cache bash

# Create sandbox user with minimal permissions
RUN addgroup -S sandbox && adduser -S sandbox -G sandbox

# Working directory where user code is mounted (read-only from host)
WORKDIR /code

# Set resource limits via JVM flags when run
# Actual CPU/memory limits are enforced by Docker at container level

USER sandbox

# The actual command is passed by DockerSandboxService:
#   sh -c "javac Solution.java 2>&1 && java -Xmx200m -cp . SolutionRunner < input.json 2>&1"
CMD ["sh"]
