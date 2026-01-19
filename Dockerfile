FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre AS runtime

COPY --from=build /app/target/app.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
