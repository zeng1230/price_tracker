FROM eclipse-temurin:17-jre

WORKDIR /app
ARG JAR_FILE=target/price-tracker-1.0.0-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
