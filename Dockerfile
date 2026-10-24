# Beinema backend (Spring Boot, Java 17) for Liara.
# Secrets come from the app's environment variables in the Liara console
# (SPRING_DATASOURCE_URL, SPRING_DATASOURCE_PASSWORD, JWT_SECRET, METIS_API_KEY, ...).

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package && cp target/*.jar /src/app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/app.jar /app/app.jar
# uploaded files live on a Liara disk mounted here (see liara.json)
ENV BEINEMA_UPLOAD_DIR=/app/uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Tehran"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
