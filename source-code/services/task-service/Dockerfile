FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
COPY services/task-service/pom.xml services/task-service/pom.xml
COPY services/task-service/src services/task-service/src
RUN mvn -q -f services/task-service/pom.xml -DskipTests package
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app
COPY --from=build /workspace/services/task-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java","-jar","app.jar"]
