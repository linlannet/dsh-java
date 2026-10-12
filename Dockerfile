FROM eclipse-temurin:17-jre-alpine AS base
WORKDIR /app

FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /src
COPY pom.xml .
COPY dsh-java-api/pom.xml dsh-java-api/
COPY dsh-java-app/pom.xml dsh-java-app/
COPY dsh-java-case/pom.xml dsh-java-case/
COPY dsh-java-domain/pom.xml dsh-java-domain/
COPY dsh-java-infrastructure/pom.xml dsh-java-infrastructure/
COPY dsh-java-trigger/pom.xml dsh-java-trigger/
COPY dsh-java-types/pom.xml dsh-java-types/
COPY plugins/sample-tools-plugin/pom.xml plugins/sample-tools-plugin/
COPY plugins/dsh-plugin-archetype/pom.xml plugins/dsh-plugin-archetype/
RUN mvn dependency:go-offline -q
COPY . .
RUN mvn package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /src/dsh-java-app/target/dsh-java-app-*-exec.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=standalone"]
