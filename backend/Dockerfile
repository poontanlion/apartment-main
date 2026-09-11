# ============================================================
# Stage 1: Build - Maven + OpenJDK 17
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml and download dependencies first (layer caching)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source code and build the jar
COPY src ./src
RUN mvn clean package -DskipTests -q

# ============================================================
# Stage 2: Run - JRE 17 Alpine (lightweight)
# ============================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy only the built jar from the builder stage
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
