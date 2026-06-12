# Dockerfile for RHCL Chatbot
# Multi-stage build for optimized image size

# Stage 1: Build the application
FROM registry.access.redhat.com/ubi9/openjdk-21:1.20 AS build

USER root

# Install Maven
RUN microdnf install -y maven && microdnf clean all

WORKDIR /build

# Copy pom.xml and download dependencies (for layer caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application (uber-jar for simpler deployment)
RUN mvn clean package -DskipTests -Dquarkus.package.jar.type=uber-jar

# Stage 2: Runtime image
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.20

WORKDIR /app

# Copy the uber-jar from build stage
COPY --from=build /build/target/*-runner.jar /app/app.jar

# Expose port 8080
EXPOSE 8080

# Set user to non-root
USER 1001

# Run the application
CMD ["java", "-jar", "/app/app.jar"]
