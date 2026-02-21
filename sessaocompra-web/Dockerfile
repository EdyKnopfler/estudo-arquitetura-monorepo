FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /app

COPY . . 

RUN --mount=type=cache,id=maven-cache,target=/root/.m2 mvn package -pl sessaocompra-web -am -DskipTests

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

COPY --from=build /app/sessaocompra-web/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]