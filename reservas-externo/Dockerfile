FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /app

COPY . . 

RUN --mount=type=cache,id=maven-cache,target=/root/.m2 mvn package -pl reservas-externo -am -DskipTests

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

COPY --from=build /app/reservas-externo/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]