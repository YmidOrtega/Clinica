FROM eclipse-temurin:21-jdk
WORKDIR /app
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser
COPY target/eureka-service-0.0.1-SNAPSHOT.jar app.jar
RUN chown appuser:appgroup /app.jar
USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]