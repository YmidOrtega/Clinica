FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --chown=app:app target/clinical-history-service-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8089
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 CMD curl -fs http://localhost:8089/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
