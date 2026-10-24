# Beinema backend (Spring Boot, Java 17) for Liara.
# Secrets come from the app's environment variables in the Liara console
# (SPRING_DATASOURCE_URL, SPRING_DATASOURCE_PASSWORD, JWT_SECRET, METIS_API_KEY, ...).
# Small Alpine images and the project's own Maven wrapper: the full maven image
# never finished downloading on Liara's builder.

FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn ./.mvn
RUN sh mvnw -B --no-transfer-progress dependency:go-offline
COPY src ./src
RUN sh mvnw -B --no-transfer-progress -DskipTests package && cp target/*.jar /src/app.jar

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /src/app.jar /app/app.jar
# uploaded files live on a Liara disk mounted here (see liara.json)
ENV BEINEMA_UPLOAD_DIR=/app/uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Tehran -Djava.awt.headless=true"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
