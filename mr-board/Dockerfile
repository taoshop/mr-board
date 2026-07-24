# Multi-stage build for Spring Boot
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY mr-board-common/pom.xml mr-board-common/
COPY mr-board-system/pom.xml mr-board-system/
COPY mr-board-web/pom.xml mr-board-web/
COPY mr-board-common/src mr-board-common/src
COPY mr-board-system/src mr-board-system/src
COPY mr-board-web/src mr-board-web/src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/mr-board-web/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
