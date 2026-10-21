# ==========================================
# 1. Build Stage (Maven + Eclipse Temurin JDK 17)
# ==========================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build production JAR
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# 2. Runtime Stage (Lightweight JRE 17 Alpine)
# ==========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create and use non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose Spring Boot default port
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
