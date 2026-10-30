# Image d'exécution seule : le JAR est construit en amont par la CI (job test)
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/dd-*.jar app.jar

# Dossier des logs (lu par logback-spring.xml via APP_LOG_DIR), accessible en écriture
# par l'utilisateur sans privilèges de l'application
ENV APP_LOG_DIR=/app/logs
RUN mkdir -p /app/logs && chown 1001:1001 /app/logs

# L'application ne tourne jamais en root : un utilisateur sans privilèges suffit
USER 1001:1001

EXPOSE 8094

# MaxRAMPercentage : la JVM adapte sa mémoire à la limite du conteneur
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
