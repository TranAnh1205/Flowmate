FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
COPY services/ai-service/pom.xml services/ai-service/pom.xml
COPY services/ai-service/src services/ai-service/src
RUN mvn -q -f services/ai-service/pom.xml -DskipTests package
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app
COPY --from=build /workspace/services/ai-service/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java","-jar","app.jar"]
