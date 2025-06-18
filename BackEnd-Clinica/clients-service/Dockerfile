FROM eclipse-temurin:21-jdk
WORKDIR /app
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser
COPY target/clients-service-0.0.1-SNAPSHOT.jar app.jar
RUN chown appuser:appgroup /app.jar
ENV SPRING_PROFILES_ACTIVE=prod
USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]
HEALTHCHECK --interval=30s --timeout=30s --start-period=5s --retries=3 CMD curl --fail http://localhost:8080/actuator/health || exit 1