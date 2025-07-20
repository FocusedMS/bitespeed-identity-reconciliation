# Use OpenJDK 17 as base image
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml from the identity-reconciliation subdirectory
COPY identity-reconciliation/mvnw .
COPY identity-reconciliation/.mvn .mvn
COPY identity-reconciliation/pom.xml .

# Make mvnw executable
RUN chmod +x mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code from the identity-reconciliation subdirectory
COPY identity-reconciliation/src ./src

# Build the application
RUN ./mvnw clean package -DskipTests

# Expose port 8080
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "target/identity-reconciliation-0.0.1-SNAPSHOT.jar"] 