# Etapa de compilacion
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN chmod +x mvnw

COPY src src

RUN ./mvnw -B -DskipTests package

# Etapa de ejecucion
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system spring && \
    useradd --system --gid spring spring

COPY --from=build /app/target/*.jar /app/app.jar

USER spring

ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]